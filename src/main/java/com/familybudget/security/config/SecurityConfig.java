package com.familybudget.security.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.familybudget.security.service.GoogleIdTokenVerifier;
import com.familybudget.security.service.SessionVersionValidator;
import com.familybudget.security.web.OAuth2LoginSuccessHandler;
import com.familybudget.security.web.ProblemResponses;
import com.familybudget.security.web.RefreshTokenCookies;
import com.familybudget.user.service.UserService;

/**
 * Two filter chains (ARCHITECTURE.md §6.1):
 * <ul>
 * <li>Google login ({@code /oauth2/**}, {@code /login/oauth2/**}): Spring's
 * {@code oauth2Login} with PKCE. It needs a session only to hold the authorization
 * request across the redirect; the authenticated result is never stored, the success
 * handler hands out our own refresh cookie instead.</li>
 * <li>Everything else: stateless API where every request outside the public endpoints
 * must carry a backend-issued JWT, validated by Spring Security's resource-server
 * support rather than a hand-written filter.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	private static final String[] AUTH_PATHS = { "/api/v1/auth/register", "/api/v1/auth/login",
			"/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/google/mobile" };

	@Bean
	@Order(1)
	SecurityFilterChain oauth2LoginSecurity(HttpSecurity http, ClientRegistrationRepository clientRegistrations,
			OAuth2LoginSuccessHandler successHandler) throws Exception {
		DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver = new DefaultOAuth2AuthorizationRequestResolver(
				clientRegistrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
		authorizationRequestResolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
		return http
			.securityMatcher("/oauth2/**", "/login/oauth2/**")
			.securityContext((context) -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
			.authorizeHttpRequests((auth) -> auth.anyRequest().permitAll())
			.oauth2Login((login) -> login
				.authorizationEndpoint((endpoint) -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
				.successHandler(successHandler)
				.failureHandler(new SimpleUrlAuthenticationFailureHandler(successHandler.getFailureUrl())))
			.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
		return http
			.csrf((csrf) -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests((auth) -> auth
				.requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers(HttpMethod.POST, AUTH_PATHS).permitAll()
				.anyRequest().authenticated())
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint(ProblemResponses.authenticationEntryPoint())
				.accessDeniedHandler(ProblemResponses.accessDeniedHandler()))
			.oauth2ResourceServer((resourceServer) -> resourceServer
				.bearerTokenResolver(bearerTokenResolver())
				.authenticationEntryPoint(ProblemResponses.authenticationEntryPoint())
				.accessDeniedHandler(ProblemResponses.accessDeniedHandler())
				.jwt(Customizer.withDefaults()))
			.build();
	}

	/**
	 * Ignores the Authorization header on the auth endpoints, so an expired access token
	 * sent along with {@code /auth/refresh} doesn't get the refresh itself rejected.
	 */
	private static BearerTokenResolver bearerTokenResolver() {
		DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
		return (request) -> request.getRequestURI().startsWith(RefreshTokenCookies.PATH + "/") ? null
				: delegate.resolve(request);
	}

	/** Not a {@code JwtDecoder} bean: that would clash with the resource server's. */
	@Bean
	GoogleIdTokenVerifier googleIdTokenVerifier(
			@Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId) {
		return new GoogleIdTokenVerifier(NimbusJwtDecoder.withJwkSetUri(GoogleIdTokenVerifier.JWK_SET_URI).build(),
				clientId);
	}

	@Bean
	SecretKey jwtSigningKey(@Value("${app.security.jwt.secret}") String secret) {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, UserService userService) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),
				new SessionVersionValidator(userService)));
		return decoder;
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSigningKey).build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${app.security.cors.allowed-origins}") List<String> allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// Needed for the httpOnly refresh-token cookie (ARCHITECTURE.md §6.2).
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

}

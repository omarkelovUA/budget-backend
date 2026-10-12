package com.familybudget.security.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Errors raised by the security filters themselves (before any controller) get the same
 * RFC 9457 shape as the rest of the API (ARCHITECTURE.md §5.4), on top of the standard
 * {@code WWW-Authenticate} header the bearer-token handlers set.
 */
public final class ProblemResponses {

	private static final String TYPE_BASE = "https://familybudget.app/problems/";

	private ProblemResponses() {
	}

	public static AuthenticationEntryPoint authenticationEntryPoint() {
		BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
		return (request, response, ex) -> {
			bearer.commence(request, response, ex);
			write(response, HttpStatus.UNAUTHORIZED, "unauthenticated", "Missing, invalid or expired access token");
		};
	}

	public static AccessDeniedHandler accessDeniedHandler() {
		BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
		return (request, response, ex) -> {
			bearer.handle(request, response, ex);
			write(response, HttpStatus.FORBIDDEN, "forbidden", "Access denied");
		};
	}

	// Fixed, trusted strings only, so plain formatting is safe without a JSON library.
	private static void write(HttpServletResponse response, HttpStatus status, String type, String detail)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter()
			.write("{\"type\":\"%s%s\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}".formatted(TYPE_BASE, type,
					status.getReasonPhrase(), status.value(), detail));
	}

}

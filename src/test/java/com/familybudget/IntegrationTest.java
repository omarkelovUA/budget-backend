package com.familybudget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Full application context against a real Postgres (Testcontainers). Every test class
 * using this shares one cached context and therefore one container. Google ID tokens
 * are trusted when signed by {@link TestGoogleIdTokens}; Google Play is {@link TestGooglePlay}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({ TestcontainersConfiguration.class, TestGoogleIdTokens.Config.class, TestGooglePlay.Config.class })
@SpringBootTest
@AutoConfigureMockMvc
public @interface IntegrationTest {

}

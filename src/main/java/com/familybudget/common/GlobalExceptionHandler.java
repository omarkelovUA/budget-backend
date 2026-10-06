package com.familybudget.common;

import java.net.URI;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates domain exceptions into RFC 9457 ProblemDetail responses with a stable
 * {@code type} the frontend can switch on (ARCHITECTURE.md §5.4). Spring MVC's own
 * exceptions are handled by the {@link ResponseEntityExceptionHandler} base class.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	static final String PROBLEM_TYPE_BASE = "https://familybudget.app/problems/";

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApiException(ApiException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		problem.setType(URI.create(PROBLEM_TYPE_BASE + ex.getType()));
		return problem;
	}

}

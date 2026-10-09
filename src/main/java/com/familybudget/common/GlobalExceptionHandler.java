package com.familybudget.common;

import java.net.URI;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates domain exceptions into RFC 9457 ProblemDetail responses with a stable
 * {@code type} the frontend can switch on (ARCHITECTURE.md §5.4). Spring MVC's own
 * exceptions are handled by the {@link ResponseEntityExceptionHandler} base class; the
 * requests it rejects as bad (invalid or unreadable bodies, wrong or missing parameters)
 * get the {@code validation-failed} type.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	static final String PROBLEM_TYPE_BASE = "https://familybudget.app/problems/";

	private static final URI ABOUT_BLANK = URI.create("about:blank");

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApiException(ApiException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		problem.setType(URI.create(PROBLEM_TYPE_BASE + ex.getType()));
		return problem;
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()
				&& response.getBody() instanceof ProblemDetail problem
				&& (problem.getType() == null || ABOUT_BLANK.equals(problem.getType()))) {
			problem.setType(URI.create(PROBLEM_TYPE_BASE + "validation-failed"));
		}
		return response;
	}

}

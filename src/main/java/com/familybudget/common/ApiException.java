package com.familybudget.common;

import org.springframework.http.HttpStatus;

/**
 * A domain error that maps to an HTTP status plus a stable problem {@code type} slug the
 * frontend can switch on (ARCHITECTURE.md §5.4), e.g. {@code email-taken}.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String type;

	public ApiException(HttpStatus status, String type, String message) {
		super(message);
		this.status = status;
		this.type = type;
	}

	public HttpStatus getStatus() {
		return this.status;
	}

	public String getType() {
		return this.type;
	}

}

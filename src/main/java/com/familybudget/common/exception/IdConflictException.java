package com.familybudget.common.exception;

import org.springframework.http.HttpStatus;

/** A client-generated id (mobile sync) that belongs to a row the caller can't see. */
public class IdConflictException extends ApiException {

	public IdConflictException() {
		super(HttpStatus.CONFLICT, "id-conflict", "This id is already taken");
	}

}

package com.familybudget.family;

enum InvitationStatus {

	PENDING, ACCEPTED, DECLINED, REVOKED,

	/** A pending invitation past its expiry, closed when the same email is invited again. */
	EXPIRED

}

package com.familybudget.transaction;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum TransactionKind {

	@JsonProperty("payment")
	PAYMENT,

	@JsonProperty("transfer")
	TRANSFER

}

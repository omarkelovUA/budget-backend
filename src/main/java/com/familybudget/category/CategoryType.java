package com.familybudget.category;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum CategoryType {

	@JsonProperty("income")
	INCOME,

	@JsonProperty("expense")
	EXPENSE

}

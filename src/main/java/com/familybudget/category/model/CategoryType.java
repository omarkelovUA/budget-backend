package com.familybudget.category.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum CategoryType {

	@JsonProperty("income")
	INCOME,

	@JsonProperty("expense")
	EXPENSE

}

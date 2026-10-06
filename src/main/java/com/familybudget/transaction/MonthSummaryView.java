package com.familybudget.transaction;

import java.math.BigDecimal;

/** One currency's payments in a month; transfers are internal moves and don't count. */
public record MonthSummaryView(String currency, BigDecimal income, BigDecimal expense) {

}

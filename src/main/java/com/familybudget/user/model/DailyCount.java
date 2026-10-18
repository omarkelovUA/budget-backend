package com.familybudget.user.model;

import java.time.LocalDate;

/** How many of something on a day (UTC). */
public record DailyCount(LocalDate day, long count) {

}

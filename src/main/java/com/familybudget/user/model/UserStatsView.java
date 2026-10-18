package com.familybudget.user.model;

import java.util.List;

/**
 * Users for the operator page. {@code signups} and {@code activeUsers} hold one entry per
 * day of the last 30, oldest first, days without any included.
 */
public record UserStatsView(long total, long newLast7Days, long newLast30Days, long activeLast30Days,
		List<DailyCount> signups, List<DailyCount> activeUsers) {

}

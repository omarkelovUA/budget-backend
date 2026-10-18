package com.familybudget.operator.model;

import com.familybudget.family.model.FamilyStatsView;
import com.familybudget.subscription.model.SubscriptionStatsView;
import com.familybudget.user.model.UserStatsView;

public record OperatorStatsView(UserStatsView users, FamilyStatsView families, SubscriptionStatsView subscriptions) {

}

package com.familybudget.family.model;

/** Families for the operator page: those with more than one member, and of those the frozen ones. */
public record FamilyStatsView(long withMembers, long frozen) {

}

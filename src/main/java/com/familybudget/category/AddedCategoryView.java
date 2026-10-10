package com.familybudget.category;

/** A family category a member other than the admin added, as the admin is told about it. */
public record AddedCategoryView(CategoryView category, String addedBy) {

}

package com.familybudget.category;

import java.util.UUID;

import com.familybudget.common.Visibility;

public record CategoryView(UUID id, CategoryType type, String name, String icon, String color, Visibility visibility) {

}

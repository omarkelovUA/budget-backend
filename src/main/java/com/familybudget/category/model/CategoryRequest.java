package com.familybudget.category.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.familybudget.common.model.Visibility;

/** Create and full update (PUT); type and visibility can't change after creation. */
public record CategoryRequest(@NotNull CategoryType type, @NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 255) String icon, @NotBlank @Size(max = 50) String color, @NotNull Visibility visibility) {

}

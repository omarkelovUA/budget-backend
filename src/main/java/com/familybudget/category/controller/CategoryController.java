package com.familybudget.category.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.category.model.AddedCategoryView;
import com.familybudget.category.model.CategoryRequest;
import com.familybudget.category.model.CategoryView;
import com.familybudget.category.service.CategoryService;
import com.familybudget.common.web.CurrentUserId;

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController {

	private final CategoryService categoryService;

	CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@GetMapping
	List<CategoryView> list(@CurrentUserId UUID userId) {
		return this.categoryService.list(userId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CategoryView create(@CurrentUserId UUID userId, @Valid @RequestBody CategoryRequest request) {
		return this.categoryService.create(userId, request);
	}

	@PutMapping("/{id}")
	CategoryView update(@CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
		return this.categoryService.update(userId, id, request);
	}

	/** The caller's own order of the categories they see. */
	@PutMapping("/order")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reorder(@CurrentUserId UUID userId, @Valid @RequestBody OrderRequest request) {
		this.categoryService.reorder(userId, request.ids());
	}

	@PostMapping("/{id}/share")
	CategoryView share(@CurrentUserId UUID userId, @PathVariable UUID id) {
		return this.categoryService.share(userId, id);
	}

	@PostMapping("/{id}/hide")
	CategoryView hide(@CurrentUserId UUID userId, @PathVariable UUID id) {
		return this.categoryService.hide(userId, id);
	}

	/** Admin only: family categories other members added that the admin hasn't seen yet. */
	@GetMapping("/added-by-members")
	List<AddedCategoryView> addedByMembers(@CurrentUserId UUID userId) {
		return this.categoryService.addedByMembers(userId);
	}

	@PostMapping("/added-by-members/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markAddedSeen(@CurrentUserId UUID userId) {
		this.categoryService.markAddedSeen(userId);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
		this.categoryService.delete(userId, id);
	}

	record OrderRequest(@NotNull @Size(max = 500) List<@NotNull UUID> ids) {
	}

}

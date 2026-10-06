package com.familybudget.category;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
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

import com.familybudget.common.CurrentUserId;

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

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
		this.categoryService.delete(userId, id);
	}

}

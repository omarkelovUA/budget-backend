package com.familybudget.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Stable JSON shape for paged lists, independent of Spring Data's {@code Page} internals. */
public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <E, T> PageView<T> of(Page<E> page, Function<List<E>, List<T>> mapper) {
		return new PageView<>(mapper.apply(page.getContent()), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

}

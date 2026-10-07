package com.familybudget.account;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.CurrentUserId;

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

	private final AccountService accountService;

	AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	/** Open accounts the caller can see; {@code ?closed=true} lists the caller's own closed ones. */
	@GetMapping
	List<AccountView> list(@CurrentUserId UUID userId, @RequestParam(defaultValue = "false") boolean closed) {
		return this.accountService.list(userId, closed);
	}

	@GetMapping("/{id}")
	AccountView get(@CurrentUserId UUID userId, @PathVariable UUID id) {
		return this.accountService.get(userId, id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AccountView create(@CurrentUserId UUID userId, @Valid @RequestBody AccountRequest request) {
		return this.accountService.create(userId, request);
	}

	@PutMapping("/{id}")
	AccountView update(@CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
		return this.accountService.update(userId, id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
		this.accountService.delete(userId, id);
	}

}

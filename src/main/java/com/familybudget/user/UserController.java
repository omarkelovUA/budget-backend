package com.familybudget.user;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.CurrentUserId;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

	private final UserService userService;

	UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	UserView me(@CurrentUserId UUID userId) {
		return this.userService.get(userId);
	}

	@GetMapping("/me/settings")
	UserSettingsView settings(@CurrentUserId UUID userId) {
		return this.userService.settings(userId);
	}

	@PutMapping("/me/settings")
	UserSettingsView updateSettings(@CurrentUserId UUID userId, @Valid @RequestBody SettingsRequest request) {
		return this.userService.updateSettings(userId, new UserSettingsView(request.defaultCurrency()));
	}

	record SettingsRequest(@NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code") String defaultCurrency) {
	}

}

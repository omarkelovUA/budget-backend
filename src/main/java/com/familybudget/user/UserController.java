package com.familybudget.user;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
		return this.userService.updateSettings(userId,
				new UserSettingsView(request.language(), request.defaultCurrency(), request.usedCurrencies(),
						request.theme(), request.primaryHue()));
	}

	record SettingsRequest(@NotNull @Pattern(regexp = "[a-z]{2}", message = "must be an ISO 639-1 code") String language,
			@NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code") String defaultCurrency,
			@NotNull @Size(min = 1, max = UserService.MAX_USED_CURRENCIES) List<@NotNull @Pattern(regexp = "[A-Z]{3}",
					message = "must be an ISO 4217 code") String> usedCurrencies,
			@NotNull String theme, @NotNull @Min(0) @Max(359) Integer primaryHue) {
	}

}

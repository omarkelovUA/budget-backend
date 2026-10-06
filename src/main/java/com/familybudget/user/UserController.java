package com.familybudget.user;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
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

}

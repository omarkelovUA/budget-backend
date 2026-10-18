package com.familybudget.operator.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.familybudget.common.exception.ApiException;
import com.familybudget.common.exception.NotFoundException;
import com.familybudget.family.service.FamilyService;
import com.familybudget.operator.model.GrantView;
import com.familybudget.operator.model.OperatorStatsView;
import com.familybudget.subscription.model.ManualGrantView;
import com.familybudget.subscription.service.SubscriptionService;
import com.familybudget.user.model.UserView;
import com.familybudget.user.service.UserService;

/** Every call is the caller's to make only if they're an operator (ARCHITECTURE.md §3.5). */
@Service
public class OperatorService {

	private final UserService userService;

	private final FamilyService familyService;

	private final SubscriptionService subscriptionService;

	OperatorService(UserService userService, FamilyService familyService, SubscriptionService subscriptionService) {
		this.userService = userService;
		this.familyService = familyService;
		this.subscriptionService = subscriptionService;
	}

	public OperatorStatsView stats(UUID operatorId) {
		this.userService.requireOperator(operatorId);
		return new OperatorStatsView(this.userService.stats(), this.familyService.stats(),
				this.subscriptionService.stats());
	}

	public List<GrantView> grants(UUID operatorId) {
		this.userService.requireOperator(operatorId);
		List<ManualGrantView> grants = this.subscriptionService.manualGrants();
		Map<UUID, UserView> users = this.userService.getAll(grants.stream().map(ManualGrantView::userId).toList())
			.stream()
			.collect(Collectors.toMap(UserView::id, Function.identity()));
		return grants.stream().map((grant) -> {
			UserView user = users.get(grant.userId());
			return new GrantView(grant.userId(), user.email(), user.displayName(), grant.expiresAt(), grant.createdAt(),
					grant.active());
		}).toList();
	}

	/** Free access for the user with this email, or a new end for the access they have. */
	public GrantView grant(UUID operatorId, String email, @Nullable Instant expiresAt) {
		this.userService.requireOperator(operatorId);
		UserView user = this.userService.findByEmail(email)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user-not-found",
					"Nobody has signed up with this email"));
		this.subscriptionService.grantManual(user.id(), expiresAt);
		return grants(operatorId).stream()
			.filter((grant) -> grant.userId().equals(user.id()))
			.findFirst()
			.orElseThrow();
	}

	public void revoke(UUID operatorId, UUID userId) {
		this.userService.requireOperator(operatorId);
		if (!this.subscriptionService.revokeManual(userId)) {
			throw new NotFoundException("This user has no manual grant");
		}
	}

}

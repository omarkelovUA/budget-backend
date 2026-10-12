package com.familybudget.sync.controller;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.web.CurrentUserId;
import com.familybudget.sync.model.SyncChangesView;
import com.familybudget.sync.service.SyncService;

@RestController
@RequestMapping("/api/v1/sync")
class SyncController {

	private final SyncService syncService;

	SyncController(SyncService syncService) {
		this.syncService = syncService;
	}

	@GetMapping("/changes")
	SyncChangesView changes(@CurrentUserId UUID userId, @RequestParam(required = false) @Nullable String since) {
		return this.syncService.changes(userId, since);
	}

}

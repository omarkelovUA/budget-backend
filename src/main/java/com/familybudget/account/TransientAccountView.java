package com.familybudget.account;

import java.math.BigDecimal;
import java.util.UUID;

/** A transient account: money from {@code senderId} waiting for {@code recipientId} to collect it. */
public record TransientAccountView(UUID id, UUID senderId, UUID recipientId, String currency, BigDecimal balance) {

}

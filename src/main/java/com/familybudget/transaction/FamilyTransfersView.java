package com.familybudget.transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Money in transit between the caller and other family members, per counterpart and currency. */
public record FamilyTransfersView(List<InTransit> outgoing, List<InTransit> incoming) {

	/** {@code id} is the transient account's; collect / take back address it. */
	public record InTransit(UUID id, Person counterpart, String currency, BigDecimal amount) {
	}

	public record Person(UUID id, String displayName) {
	}

}

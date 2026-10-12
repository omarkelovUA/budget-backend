package com.familybudget.common.entity;

import java.util.UUID;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * An entity whose id may come from the client (mobile sync, ARCHITECTURE.md §5.3), so a
 * create the app retries finds the row it already made. The id is random unless the
 * client sent one; {@link Persistable} keeps {@code save()} a plain insert either way.
 */
@MappedSuperclass
public abstract class ClientIdEntity implements Persistable<UUID> {

	@Id
	private UUID id = UUID.randomUUID();

	@Transient
	private boolean isNew = true;

	@Override
	public UUID getId() {
		return this.id;
	}

	/** Uses the client's id, if it sent one; only before the first save. */
	public void useClientId(@Nullable UUID clientId) {
		if (clientId != null) {
			this.id = clientId;
		}
	}

	@Override
	public boolean isNew() {
		return this.isNew;
	}

	@PostLoad
	@PostPersist
	void markPersisted() {
		this.isNew = false;
	}

}

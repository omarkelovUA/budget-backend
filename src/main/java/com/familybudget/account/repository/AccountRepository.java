package com.familybudget.account.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.familybudget.account.entity.AccountEntity;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

	/** Owned by the user, or the owner gave them access. */
	String VIEWABLE = "(a.ownerUserId = :userId"
			+ " or exists (select 1 from AccountEntity b join b.grants g where b.id = a.id and key(g) = :userId))";

	/** Regular accounts only: transient ones have their own listing. */
	@Query("select a from AccountEntity a where " + VIEWABLE
			+ " and a.closedAt is null and a.transientRecipientId is null order by a.createdAt")
	List<AccountEntity> findOpenViewable(UUID userId);

	@Query("select a from AccountEntity a where a.ownerUserId = :userId and a.closedAt is not null"
			+ " and a.transientRecipientId is null order by a.closedAt desc")
	List<AccountEntity> findClosedOwnedBy(UUID userId);

	/** Open transient accounts the user sends through or receives from. */
	@Query("select a from AccountEntity a where a.transientRecipientId is not null and a.closedAt is null"
			+ " and (a.ownerUserId = :userId or a.transientRecipientId = :userId) order by a.createdAt")
	List<AccountEntity> findOpenTransientsInvolving(UUID userId);

	/**
	 * Creates the transient account unless an open one exists; safe against two requests
	 * creating the same one at once (the partial unique index decides).
	 */
	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = "insert into accounts (id, owner_user_id, transient_recipient_id, name, balance,"
			+ " currency, icon, color, created_at) values (gen_random_uuid(), :senderId, :recipientId, :name, 0,"
			+ " :currency, 'assets/transit.svg', 'gray', now()) on conflict (owner_user_id, transient_recipient_id,"
			+ " currency) where transient_recipient_id is not null and closed_at is null do nothing")
	void insertTransientIfAbsent(UUID senderId, UUID recipientId, String currency, String name);

	@Query("select a from AccountEntity a where a.ownerUserId = :senderId and a.transientRecipientId = :recipientId"
			+ " and a.currency = :currency and a.closedAt is null")
	Optional<AccountEntity> findOpenTransient(UUID senderId, UUID recipientId, String currency);

	/** Only takes out what's there, so concurrent collects can't overdraw a transient account. */
	@Modifying(flushAutomatically = true)
	@Query("update AccountEntity a set a.balance = a.balance - :amount where a.id = :id and a.balance >= :amount")
	int withdraw(UUID id, BigDecimal amount);

	/** Closed accounts included, so their history stays in the transaction list. */
	@Query("select a.id from AccountEntity a where " + VIEWABLE)
	List<UUID> findViewableIds(UUID userId);

	/** Atomic, so concurrent transactions on the same account can't lose an update. */
	@Modifying
	@Query("update AccountEntity a set a.balance = a.balance + :delta where a.id = :id")
	int addToBalance(UUID id, BigDecimal delta);

	/**
	 * Closed accounts keep their history but take no new transactions and lose all grants.
	 * A bulk update, like {@link #addToBalance}, so a loaded copy can't undo a balance move.
	 * It doesn't clear the persistence context: it runs inside other modules' transactions
	 * (e.g. a family change), whose loaded entities must stay managed.
	 */
	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = "with closed as (update accounts set closed_at = now() where id = :id"
			+ " and closed_at is null returning id) delete from account_access where account_id in (select id from closed)")
	void close(UUID id);

	/** Every grant the user gives or holds; a user's grants all belong to their one family. */
	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = "delete from account_access where user_id = :userId"
			+ " or account_id in (select id from accounts where owner_user_id = :userId)")
	int deleteGrantsInvolving(UUID userId);

	List<AccountEntity> findByIdIn(Collection<UUID> ids);

	/** Regular accounts the user owns or holds a grant on, written within a sync window. */
	@Query(nativeQuery = true, value = "select a.id from accounts a where a.transient_recipient_id is null"
			+ " and a.sync_txid >= cast(cast(:since as text) as xid8) and a.sync_txid < cast(cast(:until as text) as xid8)"
			+ " and (a.owner_user_id = :userId"
			+ " or exists (select 1 from account_access g where g.account_id = a.id and g.user_id = :userId))"
			+ " limit :limit")
	List<UUID> findChangedIds(UUID userId, long since, long until, int limit);

}

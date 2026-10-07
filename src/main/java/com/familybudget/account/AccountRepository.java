package com.familybudget.account;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

	/** Owned by the user, or the owner gave them access. */
	String VIEWABLE = "(a.ownerUserId = :userId"
			+ " or exists (select 1 from AccountEntity b join b.grants g where b.id = a.id and key(g) = :userId))";

	@Query("select a from AccountEntity a where " + VIEWABLE + " and a.closedAt is null order by a.createdAt")
	List<AccountEntity> findOpenViewable(UUID userId);

	@Query("select a from AccountEntity a where a.ownerUserId = :userId and a.closedAt is not null"
			+ " order by a.closedAt desc")
	List<AccountEntity> findClosedOwnedBy(UUID userId);

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
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(nativeQuery = true, value = "with closed as (update accounts set closed_at = now() where id = :id"
			+ " and closed_at is null returning id) delete from account_access where account_id in (select id from closed)")
	void close(UUID id);

	/** Every grant the user gives or holds; a user's grants all belong to their one family. */
	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = "delete from account_access where user_id = :userId"
			+ " or account_id in (select id from accounts where owner_user_id = :userId)")
	int deleteGrantsInvolving(UUID userId);

	List<AccountEntity> findByIdIn(Collection<UUID> ids);

}

package com.familybudget.category;

import java.util.UUID;

/**
 * Published (synchronously, inside the same database transaction) when a member's
 * personal category is folded into the family category with the same type and name. The
 * transaction and budget modules repoint their rows to {@code into} before {@code from}
 * is deleted.
 */
public record CategoryMerged(UUID from, UUID into) {

}

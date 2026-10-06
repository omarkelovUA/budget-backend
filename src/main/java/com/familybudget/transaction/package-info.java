/**
 * Payments and transfers (ARCHITECTURE.md §3.1). A transaction has no owner of its own:
 * it's visible to anyone who can see one of the accounts it touches, and editable only
 * by someone who can access all of them.
 */
@NullMarked
package com.familybudget.transaction;

import org.jspecify.annotations.NullMarked;

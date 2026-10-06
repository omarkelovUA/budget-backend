package com.familybudget.account;

import java.util.UUID;

import com.familybudget.common.Visibility;

/**
 * How a transaction shows the accounts it touches: no balance, since a family member can
 * see a transfer from someone's private account without seeing that account itself.
 */
public record AccountRef(UUID id, String name, String currency, String icon, String color, Visibility visibility) {

}

package io.github.md5sha256.realty.database.entity;

import io.github.md5sha256.realty.api.Party;
import org.jetbrains.annotations.NotNull;

/**
 * A permission group that has an account, and how many contracts name it.
 *
 * @param group         the group's party, with the account it is mapped to
 * @param contractCount how many leasehold and freehold contracts name the group as their
 *                      landlord or authority
 */
public record GroupMapping(@NotNull Party.Group group, int contractCount) {
}

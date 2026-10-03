package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.api.AccountKind;
import org.jetbrains.annotations.NotNull;

/**
 * The row found when an account-backed party is looked up by {@code accountId} in {@code AccountParty} (its
 * one and only identity: the column is globally {@code UNIQUE}, not unique per kind). Carries
 * the stored {@link AccountKind} so a caller can tell a genuine match from a caller-supplied
 * kind that disagrees with what is actually on file.
 *
 * <p>Public (rather than package-private, like {@link PartySql}) because it is the return type
 * of {@link MariaPartyMapper#selectAccountRow}: the JDK dynamic proxy MyBatis builds for that
 * public interface lives in its own module and cannot reference a package-private type.</p>
 */
public record PartyAccountRow(int partyId, @NotNull AccountKind kind) {

    /**
     * @return {@link #partyId()}, once the stored kind is confirmed to match {@code requested}
     * @throws IllegalStateException if {@code requested} differs from the kind this account is
     * actually stored under
     */
    int requirePartyId(int accountId, @NotNull AccountKind requested) {
        if (kind != requested) {
            throw new IllegalStateException(
                    "account #" + accountId + " is stored as " + kind + ", not " + requested);
        }
        return partyId;
    }
}

package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decides, in one place, whether a {@code /realty set} change applies now, waits for the next
 * renewal, becomes a request to the landlord, or is refused.
 */
public final class SetRouting {

    /** What kind of ownership the region currently has. */
    public enum Tenure { NONE, FREEHOLD, VACANT_LEASE, RENTED_LEASE, ENDING_LEASE }

    /** Whether the setting is a lease term or the holder's own business. */
    public enum Kind { TERM, HOLDER }

    /** Why a change was refused. */
    public enum Refusal { NOT_HOLDER, NEEDS_NOW, NOW_NOT_PERMITTED, LEASE_ENDING,
                          UNLIMITED_NEEDS_NOW, CONSOLE_NEEDS_NOW }

    /** What to do with a change. */
    public sealed interface Outcome {
        /** Write now; {@code vacantOnly} makes the write itself refuse a region that was just rented. */
        record ApplyNow(boolean vacantOnly) implements Outcome {}

        /** Propose the change as a landlord, to take effect at the next renewal. */
        record Schedule() implements Outcome {}

        /** Propose the change as a tenant, for the landlord to accept. */
        record Request() implements Outcome {}

        /** Do nothing and tell the sender why. */
        record Refused(@NotNull Refusal reason) implements Outcome {}
    }

    private SetRouting() {
    }

    /** The region's tenure; a lease wins when both contracts are present. */
    public static @NotNull Tenure tenureOf(@Nullable FreeholdContractEntity freehold,
                                           @Nullable LeaseholdContractEntity lease) {
        if (lease != null) {
            if (lease.tenantId() == null) {
                return Tenure.VACANT_LEASE;
            }
            return lease.terminationEffectiveDate() != null ? Tenure.ENDING_LEASE : Tenure.RENTED_LEASE;
        }
        return freehold != null ? Tenure.FREEHOLD : Tenure.NONE;
    }

    /** The party that holds the region, or null when there is no contract. */
    public static @Nullable Party holderOf(@Nullable FreeholdContractEntity freehold,
                                           @Nullable LeaseholdContractEntity lease) {
        if (lease != null) {
            return lease.landlord();
        }
        if (freehold != null) {
            return freehold.titleHolderId() != null
                    ? Party.personal(freehold.titleHolderId())
                    : freehold.authority();
        }
        return null;
    }

    /** Whether the actor is a player and is the lease's tenant. */
    public static boolean isTenant(@NotNull ActorContext actor, @Nullable LeaseholdContractEntity lease) {
        return actor.player() != null && lease != null && actor.player().equals(lease.tenantId());
    }

    /** Whether a scheduled change can record a proposing player. */
    public static boolean canPropose(@NotNull ActorContext actor, @NotNull LeaseholdContractEntity lease) {
        return actor.player() != null || lease.landlord() instanceof Party.Personal;
    }

    /** Routes a change to apply now, schedule, request or refuse, following the ordered rules. */
    public static @NotNull Outcome decide(@NotNull Tenure tenure, @NotNull Kind kind,
                                          boolean tenant, boolean holds, boolean mayUseNow,
                                          boolean nowFlag, boolean unlimitedExtensions, boolean canPropose) {
        if (tenure == Tenure.NONE) {
            return new Outcome.ApplyNow(true);
        }
        if (tenure == Tenure.FREEHOLD || tenure == Tenure.VACANT_LEASE) {
            return holds
                    ? new Outcome.ApplyNow(!(nowFlag && mayUseNow))
                    : new Outcome.Refused(Refusal.NOT_HOLDER);
        }
        // The region is rented or ending.
        if (nowFlag) {
            if (!holds) {
                return new Outcome.Refused(Refusal.NOT_HOLDER);
            }
            if (!mayUseNow) {
                return new Outcome.Refused(Refusal.NOW_NOT_PERMITTED);
            }
            return new Outcome.ApplyNow(false);
        }
        if (kind == Kind.HOLDER) {
            if (!holds) {
                return new Outcome.Refused(Refusal.NOT_HOLDER);
            }
            return new Outcome.Refused(mayUseNow ? Refusal.NEEDS_NOW : Refusal.NOW_NOT_PERMITTED);
        }
        if (!tenant && !holds) {
            return new Outcome.Refused(Refusal.NOT_HOLDER);
        }
        if (tenure == Tenure.ENDING_LEASE) {
            return new Outcome.Refused(Refusal.LEASE_ENDING);
        }
        if (unlimitedExtensions) {
            return new Outcome.Refused(Refusal.UNLIMITED_NEEDS_NOW);
        }
        if (tenant) {
            return new Outcome.Request();
        }
        return canPropose ? new Outcome.Schedule() : new Outcome.Refused(Refusal.CONSOLE_NEEDS_NOW);
    }
}

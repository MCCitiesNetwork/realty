package io.github.md5sha256.realty.api;

/**
 * Lifecycle status values for a leasehold modification proposal, as stored in the
 * {@code LeaseholdModification.status} ENUM column. Centralised so Java-side comparisons reference a
 * single source of truth instead of bare string literals (the database ENUM only catches a typo at
 * runtime). Mapper SQL keeps its own literal copies because annotation strings cannot reference these.
 *
 * <p>Non-terminal: {@link #AWAITING_LANDLORD} (tenant proposal pending the landlord), {@link #ACTIVE}
 * (will apply on the next renewal). Terminal: {@link #APPLIED}, {@link #REJECTED}, {@link #WITHDRAWN},
 * {@link #SUPERSEDED}.</p>
 */
public final class LeaseholdModificationStatus {

    public static final String AWAITING_LANDLORD = "AWAITING_LANDLORD";
    public static final String ACTIVE = "ACTIVE";
    public static final String APPLIED = "APPLIED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String SUPERSEDED = "SUPERSEDED";

    private LeaseholdModificationStatus() {}
}

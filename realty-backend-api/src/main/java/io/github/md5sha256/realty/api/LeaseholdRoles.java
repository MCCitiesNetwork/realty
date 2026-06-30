package io.github.md5sha256.realty.api;

/**
 * The two leasehold party roles, as the string values stored in the {@code proposerRole}/{@code terminatedByRole}
 * columns and carried by lifecycle events. Centralised so the backend, Paper API and listeners compare against a
 * single source of truth rather than scattered string literals (the database ENUM would otherwise only catch a
 * typo at runtime).
 */
public final class LeaseholdRoles {

    public static final String LANDLORD = "landlord";
    public static final String TENANT = "tenant";

    private LeaseholdRoles() {}
}

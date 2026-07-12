-- Whether a leasehold accepts new tenants. When FALSE the region cannot be rented even while vacant;
-- it never affects the sitting tenant, only who may rent next. Toggled by /realty rentable.
ALTER TABLE LeaseholdContract
    ADD COLUMN acceptingTenants BOOLEAN NOT NULL DEFAULT TRUE;

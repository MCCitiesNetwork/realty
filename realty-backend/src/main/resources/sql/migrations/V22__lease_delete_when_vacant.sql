-- A lease its landlord asked to delete once the tenant has gone. The landlord's termination ends
-- the tenancy; the scheduled sweep then deletes the region. Existing leases are not marked.
ALTER TABLE LeaseholdContract
    ADD COLUMN deleteWhenVacant BOOLEAN NOT NULL DEFAULT FALSE;

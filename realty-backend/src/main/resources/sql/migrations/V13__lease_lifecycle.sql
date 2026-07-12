-- Rental contract lifecycle: pending modifications (proposals) + scheduled termination with notice.

-- A pending change to a leasehold's terms. At most one non-terminal row per contract.
-- A landlord proposal is created ACTIVE and applies automatically on the tenant's next renewal;
-- a tenant proposal is created AWAITING_LANDLORD and only becomes ACTIVE once the landlord accepts.
CREATE TABLE IF NOT EXISTS LeaseholdModification
(
    modificationId      INT      NOT NULL PRIMARY KEY AUTO_INCREMENT,
    leaseholdContractId INT      NOT NULL,
    proposerRole        ENUM ('landlord', 'tenant') NOT NULL,
    proposerId          UUID     NOT NULL,
    newPrice            DOUBLE,
    newDurationSeconds  BIGINT,
    newMaxExtensions    INT,
    status              ENUM ('AWAITING_LANDLORD', 'ACTIVE', 'APPLIED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED') NOT NULL,
    createdAt           DATETIME NOT NULL DEFAULT NOW(),
    resolvedAt          DATETIME
);

ALTER TABLE LeaseholdModification
    ADD (
        CONSTRAINT LeaseholdModification_LeaseholdContract_fk FOREIGN KEY (leaseholdContractId)
            REFERENCES LeaseholdContract (leaseholdContractId) ON DELETE CASCADE,
        CONSTRAINT chk_modification_price CHECK (newPrice IS NULL OR newPrice > 0),
        CONSTRAINT chk_modification_duration CHECK (newDurationSeconds IS NULL OR newDurationSeconds > 0),
        CONSTRAINT chk_modification_maxextensions CHECK (newMaxExtensions IS NULL OR newMaxExtensions >= 0)
        );

-- Index lookups of the single non-terminal modification per contract.
CREATE INDEX idx_leasehold_modification_contract ON LeaseholdModification (leaseholdContractId, status);
-- Index the outbox lookup (a player's own pending proposals).
CREATE INDEX idx_leasehold_modification_proposer ON LeaseholdModification (proposerId);
-- Index landlord-scoped lookups (inbox join + the existing per-landlord counts/lists).
CREATE INDEX idx_leasehold_contract_landlord ON LeaseholdContract (landlordId);

-- Scheduled early termination. When set, the lease ends at terminationEffectiveDate (honoured by the
-- expiry sweep) instead of waiting for endDate; terminatedByRole records who initiated it.
ALTER TABLE LeaseholdContract
    ADD COLUMN terminationEffectiveDate DATETIME,
    ADD COLUMN terminatedByRole        ENUM ('landlord', 'tenant');

ALTER TABLE LeaseholdHistory
    MODIFY COLUMN eventType ENUM (
        'RENT', 'UNRENT', 'RENEW', 'LEASEHOLD_EXPIRY',
        'SET_DURATION', 'SET_LANDLORD', 'SET_TENANT', 'UNSET_TENANT', 'SET_MAX_EXTENSIONS',
        'SET_PRICE',
        'MODIFY_PROPOSE', 'MODIFY_ACCEPT', 'MODIFY_REJECT', 'MODIFY_WITHDRAW', 'MODIFY_APPLY',
        'TERMINATE', 'TERMINATION_CANCEL'
    ) NOT NULL;

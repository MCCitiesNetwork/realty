-- Phase 1: Rename LeaseHistory → LeaseholdHistory (before altering eventType)
ALTER TABLE LeaseHistory
    MODIFY COLUMN eventType ENUM ('RENT', 'UNRENT', 'RENEW', 'LEASE_EXPIRY', 'LEASEHOLD_EXPIRY') NOT NULL;

UPDATE LeaseHistory SET eventType = 'LEASEHOLD_EXPIRY' WHERE eventType = 'LEASE_EXPIRY';

ALTER TABLE LeaseHistory
    MODIFY COLUMN eventType ENUM ('RENT', 'UNRENT', 'RENEW', 'LEASEHOLD_EXPIRY') NOT NULL;

RENAME TABLE LeaseHistory TO LeaseholdHistory;

-- Phase 2: Rename LeaseContract → LeaseholdContract
RENAME TABLE LeaseContract TO LeaseholdContract;

ALTER TABLE LeaseholdContract
    CHANGE COLUMN leaseContractId leaseholdContractId INT NOT NULL AUTO_INCREMENT;

-- Phase 3: Contract.contractType enum expand → migrate → shrink
ALTER TABLE Contract
    MODIFY COLUMN contractType ENUM ('contract', 'freehold', 'leasehold') NOT NULL;

UPDATE Contract SET contractType = 'leasehold' WHERE contractType = 'contract';

ALTER TABLE Contract
    MODIFY COLUMN contractType ENUM ('leasehold', 'freehold') NOT NULL;

-- Phase 4: Rename indexes
DROP INDEX idx_lease_contract_tenant ON LeaseholdContract;
CREATE INDEX idx_leasehold_contract_tenant ON LeaseholdContract (tenantId);

DROP INDEX idx_lease_history_region ON LeaseholdHistory;
CREATE INDEX idx_leasehold_history_region ON LeaseholdHistory (worldGuardRegionId, worldId);

DROP INDEX idx_lease_history_tenant ON LeaseholdHistory;
CREATE INDEX idx_leasehold_history_tenant ON LeaseholdHistory (tenantId);

DROP INDEX idx_lease_history_time ON LeaseholdHistory;
CREATE INDEX idx_leasehold_history_time ON LeaseholdHistory (eventTime);

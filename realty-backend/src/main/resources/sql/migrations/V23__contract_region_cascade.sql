-- Each lease and freehold row now names its region, and goes with it. Before this, only the
-- Contract link row cascaded from RealtyRegion, so deleting a region left its contract row behind.

ALTER TABLE LeaseholdContract ADD COLUMN realtyRegionId INT NULL;
UPDATE LeaseholdContract lc
    JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
    SET lc.realtyRegionId = c.realtyRegionId;
-- Rows a region delete left behind: no region reaches them. Their LeaseholdModification rows cascade.
DELETE FROM LeaseholdContract WHERE realtyRegionId IS NULL;
ALTER TABLE LeaseholdContract MODIFY realtyRegionId INT NOT NULL;
CREATE INDEX idx_leasehold_contract_region ON LeaseholdContract (realtyRegionId);
ALTER TABLE LeaseholdContract ADD CONSTRAINT fk_leasehold_contract_region
    FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE;

ALTER TABLE FreeholdContract ADD COLUMN realtyRegionId INT NULL;
UPDATE FreeholdContract fc
    JOIN Contract c ON c.contractId = fc.freeholdContractId AND c.contractType = 'freehold'
    SET fc.realtyRegionId = c.realtyRegionId;
DELETE FROM FreeholdContract WHERE realtyRegionId IS NULL;
ALTER TABLE FreeholdContract MODIFY realtyRegionId INT NOT NULL;
CREATE INDEX idx_freehold_contract_region ON FreeholdContract (realtyRegionId);
ALTER TABLE FreeholdContract ADD CONSTRAINT fk_freehold_contract_region
    FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE;

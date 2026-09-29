-- Every landlord and authority is today a bare player UUID, which forces a government or
-- business role to be faked as a UUID that happens to "own" a Treasury account. This
-- migration introduces Party, a base row that gives each party its id and kind, and one table
-- per kind for its details. Stage 1 only ever creates PERSONAL parties out of the existing
-- UUIDs; later stages add account- and group-backed rows.
--
-- MariaDB commits each statement that changes the schema (a DDL statement) by itself, so a
-- failure part-way through leaves a half-migrated schema. The statements below are ordered so
-- that no step drops data before it has been copied forward: Party is created and populated
-- first, each new *PartyId column is filled from it before the old UUID column is dropped, and
-- an old index is dropped only once nothing still depends on the column it covers.
-- The two old indexes are dropped with IF EXISTS: a database that lacks one must not stop here,
-- half-migrated.
--
-- A history row used to fill a missing tenant or buyer with the landlord's or authority's UUID,
-- which a non-player party does not have; LeaseholdHistory.tenantId and FreeholdHistory.buyerId
-- become nullable so that such a row names nobody instead. Old rows are not changed.

CREATE TABLE Party (
    partyId INT PRIMARY KEY AUTO_INCREMENT,
    kind    ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL,
    UNIQUE (partyId, kind)
);

-- Each child's kind column is the same enum as Party.kind: InnoDB compares enum foreign keys by
-- position, so a one-value enum here would not match. The CHECK pins the child to its own kind, and
-- the composite key then lets a kind row attach only to a base row of that kind.
CREATE TABLE PersonalParty (
    partyId    INT PRIMARY KEY,
    kind       ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL DEFAULT 'PERSONAL' CHECK (kind = 'PERSONAL'),
    playerUuid UUID NOT NULL UNIQUE,
    CONSTRAINT fk_personal_party FOREIGN KEY (partyId, kind) REFERENCES Party (partyId, kind)
);

CREATE TABLE AccountParty (
    partyId     INT PRIMARY KEY,
    kind        ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL DEFAULT 'ACCOUNT' CHECK (kind = 'ACCOUNT'),
    accountId   INT NOT NULL UNIQUE,
    accountKind ENUM ('BUSINESS','GOVERNMENT','SYSTEM') NOT NULL,
    CONSTRAINT fk_account_party FOREIGN KEY (partyId, kind) REFERENCES Party (partyId, kind)
);

CREATE TABLE GroupParty (
    partyId        INT PRIMARY KEY,
    kind           ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL DEFAULT 'GROUP' CHECK (kind = 'GROUP'),
    groupName      VARCHAR(64) NOT NULL UNIQUE,
    accountPartyId INT NOT NULL,
    CONSTRAINT fk_group_party FOREIGN KEY (partyId, kind) REFERENCES Party (partyId, kind),
    CONSTRAINT fk_group_party_account FOREIGN KEY (accountPartyId) REFERENCES AccountParty (partyId)
);

-- A set-based insert cannot hand back one generated id per row, so the distinct UUIDs are numbered
-- in a temporary table first and inserted into Party with those ids. AUTO_INCREMENT continues
-- after the highest one. The temporary table lives on this connection only.
CREATE TEMPORARY TABLE OldParty (
    partyId    INT PRIMARY KEY AUTO_INCREMENT,
    playerUuid UUID NOT NULL UNIQUE
);
INSERT INTO OldParty (playerUuid)
SELECT u FROM (
    SELECT landlordId AS u FROM LeaseholdContract
    UNION SELECT landlordId FROM LeaseholdHistory
    UNION SELECT authorityId FROM FreeholdContract
    UNION SELECT authorityId FROM FreeholdHistory
) AS oldParties;
INSERT INTO Party (partyId, kind) SELECT partyId, 'PERSONAL' FROM OldParty;
INSERT INTO PersonalParty (partyId, playerUuid) SELECT partyId, playerUuid FROM OldParty;
DROP TEMPORARY TABLE OldParty;

ALTER TABLE LeaseholdContract ADD COLUMN landlordPartyId INT NULL;
UPDATE LeaseholdContract lc JOIN PersonalParty p ON p.playerUuid = lc.landlordId
    SET lc.landlordPartyId = p.partyId;
DROP INDEX IF EXISTS idx_leasehold_contract_landlord ON LeaseholdContract;
ALTER TABLE LeaseholdContract DROP COLUMN landlordId, MODIFY landlordPartyId INT NOT NULL;
CREATE INDEX idx_leasehold_contract_landlord_party ON LeaseholdContract (landlordPartyId);
ALTER TABLE LeaseholdContract ADD CONSTRAINT fk_leasehold_contract_landlord
    FOREIGN KEY (landlordPartyId) REFERENCES Party (partyId);

ALTER TABLE LeaseholdHistory ADD COLUMN landlordPartyId INT NULL;
UPDATE LeaseholdHistory lh JOIN PersonalParty p ON p.playerUuid = lh.landlordId
    SET lh.landlordPartyId = p.partyId;
ALTER TABLE LeaseholdHistory DROP COLUMN landlordId, MODIFY landlordPartyId INT NOT NULL;
CREATE INDEX idx_leasehold_history_landlord_party ON LeaseholdHistory (landlordPartyId);
ALTER TABLE LeaseholdHistory ADD CONSTRAINT fk_leasehold_history_landlord
    FOREIGN KEY (landlordPartyId) REFERENCES Party (partyId);

ALTER TABLE FreeholdContract ADD COLUMN authorityPartyId INT NULL;
UPDATE FreeholdContract fc JOIN PersonalParty p ON p.playerUuid = fc.authorityId
    SET fc.authorityPartyId = p.partyId;
DROP INDEX IF EXISTS idx_freehold_contract_authority ON FreeholdContract;
ALTER TABLE FreeholdContract DROP COLUMN authorityId, MODIFY authorityPartyId INT NOT NULL;
CREATE INDEX idx_freehold_contract_authority_party ON FreeholdContract (authorityPartyId);
ALTER TABLE FreeholdContract ADD CONSTRAINT fk_freehold_contract_authority
    FOREIGN KEY (authorityPartyId) REFERENCES Party (partyId);

ALTER TABLE FreeholdHistory ADD COLUMN authorityPartyId INT NULL;
UPDATE FreeholdHistory fh JOIN PersonalParty p ON p.playerUuid = fh.authorityId
    SET fh.authorityPartyId = p.partyId;
ALTER TABLE FreeholdHistory DROP COLUMN authorityId, MODIFY authorityPartyId INT NOT NULL;
CREATE INDEX idx_freehold_history_authority_party ON FreeholdHistory (authorityPartyId);
ALTER TABLE FreeholdHistory ADD CONSTRAINT fk_freehold_history_authority
    FOREIGN KEY (authorityPartyId) REFERENCES Party (partyId);

ALTER TABLE LeaseholdHistory MODIFY tenantId UUID NULL;
ALTER TABLE FreeholdHistory MODIFY buyerId UUID NULL;

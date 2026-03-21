CREATE TABLE IF NOT EXISTS SaleHistory
(
    historyId          INT PRIMARY KEY AUTO_INCREMENT,
    worldGuardRegionId VARCHAR(255)                             NOT NULL,
    worldId            UUID                                     NOT NULL,
    eventType          ENUM ('BUY','AUCTION_BUY','OFFER_BUY')  NOT NULL,
    buyerId            UUID                                     NOT NULL,
    authorityId        UUID                                     NOT NULL,
    price              DOUBLE                                   NOT NULL,
    eventTime          DATETIME                                 NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sale_history_region ON SaleHistory (worldGuardRegionId, worldId);
CREATE INDEX idx_sale_history_buyer ON SaleHistory (buyerId);
CREATE INDEX idx_sale_history_time ON SaleHistory (eventTime);

CREATE TABLE IF NOT EXISTS LeaseHistory
(
    historyId           INT PRIMARY KEY AUTO_INCREMENT,
    worldGuardRegionId  VARCHAR(255)                             NOT NULL,
    worldId             UUID                                     NOT NULL,
    eventType           ENUM ('RENT','RENEW','LEASE_EXPIRY')     NOT NULL,
    tenantId            UUID                                     NOT NULL,
    landlordId          UUID                                     NOT NULL,
    price               DOUBLE,
    durationSeconds     BIGINT,
    extensionsRemaining INT,
    eventTime           DATETIME                                 NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lease_history_region ON LeaseHistory (worldGuardRegionId, worldId);
CREATE INDEX idx_lease_history_tenant ON LeaseHistory (tenantId);
CREATE INDEX idx_lease_history_time ON LeaseHistory (eventTime);

CREATE TABLE IF NOT EXISTS FreeholdHistory
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

CREATE INDEX idx_freehold_history_region ON FreeholdHistory (worldGuardRegionId, worldId);
CREATE INDEX idx_freehold_history_buyer ON FreeholdHistory (buyerId);
CREATE INDEX idx_freehold_history_time ON FreeholdHistory (eventTime);

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

CREATE TABLE IF NOT EXISTS AgentHistory
(
    historyId          INT PRIMARY KEY AUTO_INCREMENT,
    worldGuardRegionId VARCHAR(255)                             NOT NULL,
    worldId            UUID                                     NOT NULL,
    eventType          ENUM ('AGENT_ADD','AGENT_REMOVE')        NOT NULL,
    agentId            UUID                                     NOT NULL,
    actorId            UUID                                     NOT NULL,
    eventTime          DATETIME                                 NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agent_history_region ON AgentHistory (worldGuardRegionId, worldId);
CREATE INDEX idx_agent_history_agent ON AgentHistory (agentId);
CREATE INDEX idx_agent_history_time ON AgentHistory (eventTime);

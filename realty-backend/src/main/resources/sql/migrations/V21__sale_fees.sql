-- Sale fees: the fees players set on a region, the levies chosen for regions and authorities,
-- the lines of money each sale owes its recipients, and refunds that could not be paid.

CREATE TABLE RegionFee (
    regionFeeId      INT AUTO_INCREMENT PRIMARY KEY,
    realtyRegionId   INT NOT NULL,
    recipientPartyId INT NOT NULL,
    side             ENUM('BUYER','SELLER') NOT NULL,
    percent          DOUBLE NOT NULL CHECK (percent > 0),
    role             ENUM('authority','titleholder','agent') NOT NULL,
    setBy            UUID NOT NULL,
    approved         BOOLEAN NOT NULL,
    createdAt        DATETIME NOT NULL DEFAULT NOW(),
    UNIQUE (realtyRegionId, recipientPartyId),
    FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE,
    FOREIGN KEY (recipientPartyId) REFERENCES Party (partyId)
);

CREATE TABLE RegionLevy (
    realtyRegionId INT PRIMARY KEY,
    levyName VARCHAR(64) NOT NULL,
    setBy UUID NULL,
    setAt DATETIME NOT NULL DEFAULT NOW(),
    FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE
);

CREATE TABLE AuthorityLevy (
    authorityPartyId INT PRIMARY KEY,
    levyName VARCHAR(64) NOT NULL,
    setBy UUID NULL,
    setAt DATETIME NOT NULL DEFAULT NOW(),
    FOREIGN KEY (authorityPartyId) REFERENCES Party (partyId)
);

CREATE TABLE SaleFee (
    saleFeeId        INT AUTO_INCREMENT PRIMARY KEY,
    saleKind         ENUM('buy','bid','offer') NOT NULL,
    saleRef          INT NULL,            -- bidId or offerId; NULL for a buy
    realtyRegionId   INT NULL,
    regionName       VARCHAR(255) NOT NULL,
    worldId          UUID NOT NULL,
    buyerId          UUID NOT NULL,
    lineKind         ENUM('fee','proceeds') NOT NULL,
    source           VARCHAR(255) NOT NULL,
    recipientPartyId INT NOT NULL,
    side             ENUM('BUYER','SELLER') NOT NULL,
    percent          DOUBLE NOT NULL,
    amount           DOUBLE NOT NULL CHECK (amount >= 0),
    status           ENUM('locked','paying','paid','failed') NOT NULL DEFAULT 'locked',
    completedAt      DATETIME NULL,
    attempts         INT NOT NULL DEFAULT 0,
    lastAttemptAt    DATETIME NULL,
    lastError        VARCHAR(1024) NULL,
    INDEX (saleKind, saleRef),
    INDEX (status, completedAt),
    FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE SET NULL,
    FOREIGN KEY (recipientPartyId) REFERENCES Party (partyId)
);

CREATE TABLE PendingRefund (
    pendingRefundId INT AUTO_INCREMENT PRIMARY KEY,
    buyerId         UUID NOT NULL,
    amount          DOUBLE NOT NULL CHECK (amount > 0),
    regionName      VARCHAR(255) NOT NULL,
    worldId         UUID NULL,
    reason          VARCHAR(64) NOT NULL,   -- 'bid-expired' | 'offer-expired' | 'region-deleted'
    status          ENUM('pending','paying') NOT NULL DEFAULT 'pending',
    attempts        INT NOT NULL DEFAULT 0,
    lastAttemptAt   DATETIME NULL,
    lastError       VARCHAR(1024) NULL
);

-- A payment now owes its total, the price plus any buyer-side fees. A payment begun before
-- sale fees owes its price and keeps the old rules: it pays the seller directly.
ALTER TABLE FreeholdContractBidPayment
    ADD COLUMN totalDue DOUBLE NULL;
UPDATE FreeholdContractBidPayment SET totalDue = bidPrice;
ALTER TABLE FreeholdContractBidPayment
    ADD COLUMN oldRules BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE FreeholdContractBidPayment SET oldRules = TRUE;
-- An instalment is pending from when it is counted until its money has moved: confirmed money is
-- in currentPayment, an instalment whose transfer has not finished is in pendingPayment.
ALTER TABLE FreeholdContractBidPayment
    MODIFY COLUMN totalDue DOUBLE NOT NULL,
    ADD COLUMN pendingPayment DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN pendingSince DATETIME NULL,
    DROP CONSTRAINT chk_valid_bid_payment_currentPayment,
    ADD CONSTRAINT chk_valid_bid_payment_currentPayment
        CHECK (currentPayment >= 0 AND pendingPayment >= 0
               AND currentPayment + pendingPayment <= totalDue + 0.005);

ALTER TABLE FreeholdContractOfferPayment
    ADD COLUMN totalDue DOUBLE NULL;
UPDATE FreeholdContractOfferPayment SET totalDue = offerPrice;
ALTER TABLE FreeholdContractOfferPayment
    ADD COLUMN oldRules BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE FreeholdContractOfferPayment SET oldRules = TRUE;
ALTER TABLE FreeholdContractOfferPayment
    MODIFY COLUMN totalDue DOUBLE NOT NULL,
    ADD COLUMN acceptedBy UUID NULL,
    ADD COLUMN pendingPayment DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN pendingSince DATETIME NULL,
    DROP CONSTRAINT chk_valid_currentPayment,
    ADD CONSTRAINT chk_valid_currentPayment
        CHECK (currentPayment >= 0 AND pendingPayment >= 0
               AND currentPayment + pendingPayment <= totalDue + 0.005);

-- Before this version, a completed auction was never marked ended, so the scheduler would offer
-- the sold plot to its winner again. An auction that is not ended, has no payment waiting, and
-- whose plot was sold at auction after it started is such an auction: it is marked ended. One
-- still waiting for the scheduler to settle it has no such sale and is left as it is.
UPDATE FreeholdContractAuction sca
INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
SET sca.ended = TRUE
WHERE sca.ended = FALSE
AND NOT EXISTS (SELECT 1 FROM FreeholdContractBidPayment bp
                WHERE bp.freeholdContractAuctionId = sca.freeholdContractAuctionId)
AND EXISTS (SELECT 1 FROM FreeholdHistory fh
            WHERE fh.worldGuardRegionId = rr.worldGuardRegionId
            AND fh.worldId = rr.worldId
            AND fh.eventType = 'AUCTION_BUY'
            AND fh.eventTime >= sca.startDate);

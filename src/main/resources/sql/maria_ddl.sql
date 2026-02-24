CREATE TABLE IF NOT EXISTS RealtyRegion
(
    realtyRegionId     INT PRIMARY KEY AUTO_INCREMENT,
    worldGuardRegionId VARCHAR(255) NOT NULL,
    worldId            UUID         NOT NULL,
    contractId         INT
    );

CREATE TABLE IF NOT EXISTS Contract
(
    contractId     INT                       NOT NULL,
    contractType   ENUM ('contract', 'sale') NOT NULL,
    realtyRegionId INT                       NOT NULL,
    PRIMARY KEY (contractId, contractType)
    );

CREATE TABLE IF NOT EXISTS LeaseContract
(
    leaseContractId      INT      NOT NULL PRIMARY KEY AUTO_INCREMENT,
    tenantId             UUID     NOT NULL,
    price                DOUBLE   NOT NULL,
    durationSeconds      LONG     NOT NULL,
    startDate            DATETIME NOT NULL,
    currentMaxExtensions INT,
    maxExtensions        INT
);

CREATE TABLE IF NOT EXISTS SaleContract
(
    saleContractId INT    NOT NULL PRIMARY KEY AUTO_INCREMENT,
    authorityId    UUID   NOT NULL,
    titleHolderId  UUID   NOT NULL,
    price          DOUBLE NOT NULL
);

CREATE TABLE IF NOT EXISTS SaleContractAuction
(
    saleContractAuctionId  INT      NOT NULL PRIMARY KEY AUTO_INCREMENT,
    startDate              DATETIME NOT NULL,
    biddingDurationSeconds LONG     NOT NULL,
    paymentDurationSeconds LONG     NOT NULL,
    minBid                 DOUBLE   NOT NULL,
    minStep                DOUBLE   NOT NULL,
    currentBidderId        UUID,
    currentBidPrice        DOUBLE
);

ALTER TABLE RealtyRegion
    ADD (
        CONSTRAINT RealtyRegion_Contract_contractId_fk FOREIGN KEY (contractId) REFERENCES Contract (contractId),
        CONSTRAINT unique_worldGuardRegionId_worldId UNIQUE (worldGuardRegionId, worldId)
        );

ALTER TABLE Contract
    ADD (
        CONSTRAINT Contract_RealtyRegion_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId)
        );

ALTER TABLE LeaseContract
    ADD (
        CONSTRAINT chk_price CHECK (price > 0),
        CONSTRAINT chk_duration CHECK (durationSeconds > 0),
        CONSTRAINT chk_extensions CHECK ((maxExtensions IS NOT NULL AND currentMaxExtensions IS NOT NULL AND
                                          currentMaxExtensions <= maxExtensions) OR
                                         (maxExtensions IS NULL AND currentMaxExtensions IS NULL))
        );

ALTER TABLE SaleContract
    ADD (
        CONSTRAINT chk_price CHECK (price > 0)
        );

ALTER TABLE SaleContractAuction
    ADD (
        CONSTRAINT chk_bid CHECK ((currentBidderId IS NULL AND currentBidPrice IS NULL) OR
                                  (currentBidderId IS NOT NULL AND currentBidPrice IS NOT NULL)),
        CONSTRAINT chk_valid_bidPrice CHECK (currentBidPrice > 0),
        CONSTRAINT chk_valid_minBid CHECK (minBid > 0),
        CONSTRAINT chk_valid_minStep CHECK (minStep > 0),
        CONSTRAINT chk_valid_biddingDuration CHECK ( biddingDurationSeconds > 0 ),
        CONSTRAINT chk_valid_paymentDuration CHECK ( paymentDurationSeconds > 0 )
        );
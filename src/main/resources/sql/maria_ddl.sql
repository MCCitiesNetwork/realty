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
    realtyRegionId         INT      NOT NULL,
    startDate              DATETIME NOT NULL,
    biddingDurationSeconds LONG     NOT NULL,
    paymentDurationSeconds LONG     NOT NULL,
    paymentDeadline        DATETIME NOT NULL DEFAULT (startDate +
    INTERVAL (biddingDurationSeconds + paymentDurationSeconds) SECOND),
    minBid                 DOUBLE   NOT NULL,
    minStep                DOUBLE   NOT NULL,
    ended                  BOOL     NOT NULL DEFAULT FALSE
    );

CREATE TABLE IF NOT EXISTS SaleContractBid
(
    bidId                 INT      NOT NULL PRIMARY KEY AUTO_INCREMENT,
    saleContractAuctionId INT      NOT NULL,
    bidderId              UUID     NOT NULL,
    bidPrice              DOUBLE   NOT NULL,
    bidTime               DATETIME NOT NULL DEFAULT NOW(),
    PRIMARY KEY (saleContractAuctionId, bidderId, bidPrice)
    );

CREATE TABLE IF NOT EXISTS SaleContractOffer
(
    offerId INT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    realtyRegionId INT NOT NULL
    offererId UUID NOT NULL
    offerPrice DOUBLE NOT NULL,
    offerTime DATETIME NOT NULL DEFAULT NOW()
);

ALTER TABLE SaleContractOffer
 ADD (
        CONSTRAINT RealtyRegion_SaleContractOffer_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion(realtyRegionId),
        CONSTRAINT unique_offer UNIQUE (realtyRegionId, offererId),
        CONSTRAINT chk_valid_offerPrice CHECK (offerPrice > 0),
        CONSTRAINT chk_offerer_not_authority CHECK (
            NOT EXISTS (
                SELECT 1
                FROM RealtyRegion rr
                         JOIN Contract c ON c.contractId = rr.contractId AND c.contractType = 'sale'
                         JOIN SaleContract sc ON sc.saleContractId = c.contractId
                WHERE rr.realtyRegionId = realtyRegionId
                  AND sc.authorityId = offererId
            )
        )
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
        CONSTRAINT SaleContractAuction_RealtyRegion_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId),
        CONSTRAINT chk_valid_minBid CHECK (minBid > 0),
        CONSTRAINT chk_valid_minStep CHECK (minStep > 0),
        CONSTRAINT chk_valid_biddingDuration CHECK ( biddingDurationSeconds > 0 ),
        CONSTRAINT chk_valid_paymentDuration CHECK ( paymentDurationSeconds > 0 ),
        CONSTRAINT chk_unique_active_auction_per_region CHECK (
            ended = TRUE OR NOT EXISTS (
                SELECT 1 FROM SaleContractAuction sca
                WHERE sca.realtyRegionId = realtyRegionId
                AND sca.saleContractAuctionId != saleContractAuctionId
                AND sca.ended = FALSE
            )
        )
        );

ALTER TABLE SaleContractBid
    ADD (
        CONSTRAINT SaleContractAuction_SaleContractBid_saleContractAuctionId_fk FOREIGN KEY (saleContractAuctionId) REFERENCES SaleContractAuction (saleContractAuctionId),
        CONSTRAINT chk_valid_bidPrice CHECK (bidPrice > 0)
        );
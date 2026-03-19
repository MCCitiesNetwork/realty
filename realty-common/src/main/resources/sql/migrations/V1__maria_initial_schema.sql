CREATE TABLE IF NOT EXISTS RealtyRegion
(
    realtyRegionId     INT PRIMARY KEY AUTO_INCREMENT,
    worldGuardRegionId VARCHAR(255) NOT NULL,
    worldId            UUID         NOT NULL
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
    landlordId           UUID     NOT NULL,
    tenantId             UUID,
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
    titleHolderId  UUID,
    price          DOUBLE
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
    bidTime               DATETIME NOT NULL DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS SaleContractOffer
(
    offerId        INT      NOT NULL PRIMARY KEY AUTO_INCREMENT,
    realtyRegionId INT      NOT NULL,
    offererId      UUID     NOT NULL,
    offerPrice     DOUBLE   NOT NULL,
    offerTime      DATETIME NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS SaleContractOfferPayment
(
    offerId         INT      NOT NULL PRIMARY KEY,
    realtyRegionId  INT      NOT NULL,
    offererId       UUID     NOT NULL,
    offerPrice      DOUBLE   NOT NULL,
    paymentDeadline DATETIME NOT NULL,
    currentPayment  DOUBLE   NOT NULL
);


CREATE TABLE IF NOT EXISTS SaleContractBidPayment
(
    bidId                 INT      NOT NULL PRIMARY KEY,
    saleContractAuctionId INT      NOT NULL,
    realtyRegionId        INT      NOT NULL,
    bidderId              UUID     NOT NULL,
    bidPrice              DOUBLE   NOT NULL,
    paymentDeadline       DATETIME NOT NULL,
    currentPayment        DOUBLE   NOT NULL
);


ALTER TABLE SaleContractOfferPayment
    ADD (
        CONSTRAINT SaleContractOffer_SaleContractOfferPayment_offerId_fk FOREIGN KEY (offerId) REFERENCES SaleContractOffer (offerId) ON DELETE CASCADE,
        CONSTRAINT RealtyRegion_SaleContractOfferPayment_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE,
        CONSTRAINT unique_payment UNIQUE (realtyRegionId, offererId),
        CONSTRAINT chk_valid_offerPrice CHECK (offerPrice > 0),
        CONSTRAINT chk_valid_currentPayment CHECK (currentPayment >= 0 AND currentPayment <= offerPrice)
        );

ALTER TABLE SaleContractOffer
    ADD (
        -- Cascade: deleting a RealtyRegion removes all its pending offers.
        CONSTRAINT RealtyRegion_SaleContractOffer_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE,
        CONSTRAINT unique_offer UNIQUE (realtyRegionId, offererId),
        CONSTRAINT chk_valid_offerPrice CHECK (offerPrice > 0)
        -- chk_offerer_not_authority: enforced in application logic (MariaDB does not support subqueries in CHECK)
     );

ALTER TABLE RealtyRegion
    ADD (
        CONSTRAINT unique_worldGuardRegionId_worldId UNIQUE (worldGuardRegionId, worldId)
        );

ALTER TABLE Contract
    ADD (
        -- Cascade: deleting a RealtyRegion removes all its Contract records.
        CONSTRAINT Contract_RealtyRegion_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE
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
        CONSTRAINT chk_price CHECK (price IS NULL OR price > 0)
        );

ALTER TABLE SaleContractAuction
    ADD (
        -- Cascade: deleting a RealtyRegion removes all its auctions.
        CONSTRAINT SaleContractAuction_RealtyRegion_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE,
        CONSTRAINT chk_valid_minBid CHECK (minBid > 0),
        CONSTRAINT chk_valid_minStep CHECK (minStep > 0),
        CONSTRAINT chk_valid_biddingDuration CHECK ( biddingDurationSeconds > 0 ),
        CONSTRAINT chk_valid_paymentDuration CHECK ( paymentDurationSeconds > 0 )
        -- chk_unique_active_auction_per_region: enforced in application logic (MariaDB does not support subqueries in CHECK)
        );

ALTER TABLE SaleContractBid
    ADD (
        -- Cascade: deleting an auction removes all its bids.
        CONSTRAINT SaleContractAuction_SaleContractBid_saleContractAuctionId_fk FOREIGN KEY (saleContractAuctionId) REFERENCES SaleContractAuction (saleContractAuctionId) ON DELETE CASCADE,
        CONSTRAINT chk_valid_bidPrice CHECK (bidPrice > 0),
        CONSTRAINT unique_sale_contract UNIQUE (saleContractAuctionId, bidderId, bidPrice)
        );

ALTER TABLE SaleContractBidPayment
    ADD (
        CONSTRAINT SaleContractBid_BidPayment_bidId_fk FOREIGN KEY (bidId) REFERENCES SaleContractBid (bidId) ON DELETE CASCADE,
        CONSTRAINT SaleContractAuction_BidPayment_auctionId_fk FOREIGN KEY (saleContractAuctionId) REFERENCES SaleContractAuction (saleContractAuctionId) ON DELETE CASCADE,
        CONSTRAINT RealtyRegion_BidPayment_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE,
        CONSTRAINT unique_bid_payment UNIQUE (saleContractAuctionId, bidderId),
        CONSTRAINT chk_valid_bid_payment_bidPrice CHECK (bidPrice > 0),
        CONSTRAINT chk_valid_bid_payment_currentPayment CHECK (currentPayment >= 0 AND currentPayment <= bidPrice)
        );

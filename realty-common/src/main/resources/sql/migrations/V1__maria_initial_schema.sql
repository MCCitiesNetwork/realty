CREATE TABLE IF NOT EXISTS RealtyRegion
(
    realtyRegionId     INT PRIMARY KEY AUTO_INCREMENT,
    worldGuardRegionId VARCHAR(255) NOT NULL,
    worldId            UUID         NOT NULL
    );

CREATE TABLE IF NOT EXISTS Contract
(
    contractId     INT                       NOT NULL,
    contractType   ENUM ('contract', 'freehold') NOT NULL,
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

CREATE TABLE IF NOT EXISTS FreeholdContract
(
    freeholdContractId INT    NOT NULL PRIMARY KEY AUTO_INCREMENT,
    authorityId        UUID   NOT NULL,
    titleHolderId      UUID,
    price              DOUBLE
);

CREATE TABLE IF NOT EXISTS FreeholdContractAuction
(
    freeholdContractAuctionId  INT      NOT NULL PRIMARY KEY AUTO_INCREMENT,
    realtyRegionId         INT      NOT NULL,
    auctioneerId           UUID     NOT NULL,
    startDate              DATETIME NOT NULL,
    biddingDurationSeconds LONG     NOT NULL,
    paymentDurationSeconds LONG     NOT NULL,
    paymentDeadline        DATETIME NOT NULL DEFAULT (startDate +
    INTERVAL (biddingDurationSeconds + paymentDurationSeconds) SECOND),
    minBid                 DOUBLE   NOT NULL,
    minStep                DOUBLE   NOT NULL,
    ended                  BOOL     NOT NULL DEFAULT FALSE
    );

CREATE TABLE IF NOT EXISTS FreeholdContractBid
(
    bidId                 INT      NOT NULL PRIMARY KEY AUTO_INCREMENT,
    freeholdContractAuctionId INT      NOT NULL,
    bidderId              UUID     NOT NULL,
    bidPrice              DOUBLE   NOT NULL,
    bidTime               DATETIME NOT NULL DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS FreeholdContractOffer
(
    offerId        INT      NOT NULL PRIMARY KEY AUTO_INCREMENT,
    realtyRegionId INT      NOT NULL,
    offererId      UUID     NOT NULL,
    offerPrice     DOUBLE   NOT NULL,
    offerTime      DATETIME NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS FreeholdContractOfferPayment
(
    offerId         INT      NOT NULL PRIMARY KEY,
    realtyRegionId  INT      NOT NULL,
    offererId       UUID     NOT NULL,
    offerPrice      DOUBLE   NOT NULL,
    paymentDeadline DATETIME NOT NULL,
    currentPayment  DOUBLE   NOT NULL
);


CREATE TABLE IF NOT EXISTS FreeholdContractBidPayment
(
    bidId                 INT      NOT NULL PRIMARY KEY,
    freeholdContractAuctionId INT      NOT NULL,
    realtyRegionId        INT      NOT NULL,
    bidderId              UUID     NOT NULL,
    bidPrice              DOUBLE   NOT NULL,
    paymentDeadline       DATETIME NOT NULL,
    currentPayment        DOUBLE   NOT NULL
);

CREATE TABLE IF NOT EXISTS FreeholdContractSanctionedAuctioneers
(
    realtyRegionId INT  NOT NULL,
    auctioneerId   UUID NOT NULL,
    PRIMARY KEY (realtyRegionId, auctioneerId)
);


ALTER TABLE FreeholdContractOfferPayment
    ADD (
        CONSTRAINT FreeholdContractOffer_FreeholdContractOfferPayment_offerId_fk FOREIGN KEY (offerId) REFERENCES FreeholdContractOffer (offerId) ON DELETE CASCADE,
        CONSTRAINT RealtyRegion_FreeholdContractOfferPayment_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE,
        CONSTRAINT unique_payment UNIQUE (realtyRegionId, offererId),
        CONSTRAINT chk_valid_offerPrice CHECK (offerPrice > 0),
        CONSTRAINT chk_valid_currentPayment CHECK (currentPayment >= 0 AND currentPayment <= offerPrice)
        );

ALTER TABLE FreeholdContractOffer
    ADD (
        -- Cascade: deleting a RealtyRegion removes all its pending offers.
        CONSTRAINT RealtyRegion_FreeholdContractOffer_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE,
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

ALTER TABLE FreeholdContract
    ADD (
        CONSTRAINT chk_price CHECK (price IS NULL OR price > 0)
        );

ALTER TABLE FreeholdContractAuction
    ADD (
        -- Cascade: deleting a RealtyRegion removes all its auctions.
        CONSTRAINT FreeholdContractAuction_RealtyRegion_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE,
        CONSTRAINT chk_valid_minBid CHECK (minBid > 0),
        CONSTRAINT chk_valid_minStep CHECK (minStep > 0),
        CONSTRAINT chk_valid_biddingDuration CHECK ( biddingDurationSeconds > 0 ),
        CONSTRAINT chk_valid_paymentDuration CHECK ( paymentDurationSeconds > 0 )
        -- chk_unique_active_auction_per_region: enforced in application logic (MariaDB does not support subqueries in CHECK)
        );

ALTER TABLE FreeholdContractBid
    ADD (
        -- Cascade: deleting an auction removes all its bids.
        CONSTRAINT fk_FreeholdContractBid_auctionId FOREIGN KEY (freeholdContractAuctionId) REFERENCES FreeholdContractAuction (freeholdContractAuctionId) ON DELETE CASCADE,
        CONSTRAINT chk_valid_bidPrice CHECK (bidPrice > 0),
        CONSTRAINT unique_freehold_contract UNIQUE (freeholdContractAuctionId, bidderId, bidPrice)
        );

ALTER TABLE FreeholdContractSanctionedAuctioneers
    ADD (
        CONSTRAINT SanctionedAuctioneers_RealtyRegion_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE
        );

ALTER TABLE FreeholdContractBidPayment
    ADD (
        CONSTRAINT FreeholdContractBid_BidPayment_bidId_fk FOREIGN KEY (bidId) REFERENCES FreeholdContractBid (bidId) ON DELETE CASCADE,
        CONSTRAINT FreeholdContractAuction_BidPayment_auctionId_fk FOREIGN KEY (freeholdContractAuctionId) REFERENCES FreeholdContractAuction (freeholdContractAuctionId) ON DELETE CASCADE,
        CONSTRAINT RealtyRegion_BidPayment_realtyRegionId_fk FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE,
        CONSTRAINT unique_bid_payment UNIQUE (freeholdContractAuctionId, bidderId),
        CONSTRAINT chk_valid_bid_payment_bidPrice CHECK (bidPrice > 0),
        CONSTRAINT chk_valid_bid_payment_currentPayment CHECK (currentPayment >= 0 AND currentPayment <= bidPrice)
        );

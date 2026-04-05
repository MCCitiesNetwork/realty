-- Contract: composite index for the ubiquitous (realtyRegionId, contractType) join filter
CREATE INDEX idx_contract_region_type ON Contract (realtyRegionId, contractType);

-- FreeholdContract: player UUID lookups for "my regions" queries
CREATE INDEX idx_freehold_contract_title_holder ON FreeholdContract (titleHolderId);
CREATE INDEX idx_freehold_contract_authority ON FreeholdContract (authorityId);

-- LeaseContract: tenant UUID lookup for "my leases" queries
CREATE INDEX idx_lease_contract_tenant ON LeaseContract (tenantId);

-- FreeholdContractAuction: active auction lookup per region
CREATE INDEX idx_auction_region_ended ON FreeholdContractAuction (realtyRegionId, ended);

-- FreeholdContractBid: highest bid selection (ORDER BY bidPrice DESC LIMIT 1)
CREATE INDEX idx_bid_auction_price ON FreeholdContractBid (freeholdContractAuctionId, bidPrice DESC);

-- FreeholdContractOfferPayment / FreeholdContractBidPayment: expired payment scans
CREATE INDEX idx_offer_payment_deadline ON FreeholdContractOfferPayment (paymentDeadline);
CREATE INDEX idx_bid_payment_deadline ON FreeholdContractBidPayment (paymentDeadline);

-- FreeholdContractOffer: outbound offer lookup by offerer across all regions
CREATE INDEX idx_offer_offerer ON FreeholdContractOffer (offererId);

-- FreeholdContractBidPayment: region-scoped lookups and deletes
CREATE INDEX idx_bid_payment_region ON FreeholdContractBidPayment (realtyRegionId);

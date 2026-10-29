-- A freehold no longer accepts offers until its titleholder turns them on. Existing
-- freeholds turn off too, except one with an offer on it: that offer may be open, or
-- accepted and being paid for (a payment row always keeps its offer row), and turning
-- the freehold off under it would strand the buyer.
ALTER TABLE FreeholdContract
    ALTER COLUMN acceptingOffers SET DEFAULT FALSE;

UPDATE FreeholdContract fc
    INNER JOIN Contract c ON c.contractId = fc.freeholdContractId AND c.contractType = 'freehold'
SET fc.acceptingOffers = FALSE
WHERE NOT EXISTS (SELECT 1 FROM FreeholdContractOffer o WHERE o.realtyRegionId = c.realtyRegionId);

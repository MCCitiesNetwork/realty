package io.github.md5sha256.realty.database.entity;

/**
 * The whole estate in numbers, read in a single statement.
 *
 * <p>Each figure has a counter of its own on the backend, and a dashboard that wanted
 * all ten paid ten round trips. This is the same ten questions asked once; every
 * figure here is defined exactly as its standalone counterpart is.</p>
 *
 * @param regions                         Every registered region, contract or not
 * @param freeholdContracts               Every freehold contract
 * @param occupiedFreeholds               Freeholds with a title holder
 * @param averageFreeholdPrice            Mean asking price over priced freeholds; zero with none
 * @param leaseholdContracts              Every leasehold contract
 * @param occupiedLeaseholds              Leaseholds with a tenant
 * @param averageLeaseholdPrice           Mean rent over every leasehold; zero with none
 * @param averageLeaseholdDurationSeconds Mean span, in whole seconds, from the start to
 *                                        the end of the leases currently let; zero with none.
 *                                        Not the contracts' nominal term: a let that has
 *                                        been extended runs longer than its term says
 * @param activeOffers                    Offers currently standing
 * @param activeAuctions                  Auctions still taking bids
 */
public record StatisticsEntity(
        int regions,
        int freeholdContracts,
        int occupiedFreeholds,
        double averageFreeholdPrice,
        int leaseholdContracts,
        int occupiedLeaseholds,
        double averageLeaseholdPrice,
        long averageLeaseholdDurationSeconds,
        int activeOffers,
        int activeAuctions
) {
}

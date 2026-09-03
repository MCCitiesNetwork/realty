package io.github.md5sha256.realty.database.entity;

/**
 * Ordering for the live-auction listing.
 *
 * <p>An enum rather than a raw string because the value reaches an {@code ORDER BY}
 * clause, and the mapper chooses between fixed clauses by branching on it -- a caller
 * never supplies SQL text.</p>
 */
public enum AuctionSort {

    /** Soonest bidding deadline first -- what a countdown panel wants. */
    ENDING_SOON,

    /** Largest standing bid first, an auction with no bid ranking at its minimum. */
    HIGHEST_BID
}

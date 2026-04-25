package io.github.md5sha256.realty.database.entity;

/**
 * Filter for region search results based on whether the region is occupied.
 * A freehold region is occupied when its title holder is non-null;
 * a leasehold region is occupied when its tenant is non-null.
 */
public enum OccupancyFilter {
    IGNORE,
    OCCUPIED,
    UNOCCUPIED
}

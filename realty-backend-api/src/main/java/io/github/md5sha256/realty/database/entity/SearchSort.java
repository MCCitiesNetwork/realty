package io.github.md5sha256.realty.database.entity;

/**
 * Sort order for region search results.
 *
 * <p>A bound choice rather than a free-form column/direction string: the mapper
 * maps each constant to one fixed {@code ORDER BY} clause, so no caller-supplied
 * text is ever interpolated into the SQL.</p>
 */
public enum SearchSort {
    PRICE_DESC,
    PRICE_ASC
}

package io.github.md5sha256.realty.rest;

/**
 * The cache lifetimes this API hands to browsers and proxies.
 *
 * <p>Nothing here is stale-tolerant for long. The register changes as players trade,
 * and a listing site showing a plot as vacant a minute after it was let is wrong in
 * a way a visitor notices. A minute is enough to absorb a page's own repeated asks
 * -- two components wanting the tag list, a back-button return to the front page --
 * without a visitor ever seeing yesterday's market.</p>
 */
final class ResponseCaching {

    /** For answers every page asks for and that change slowly: worlds, tags, totals. */
    static final String SHORT_LIVED = "public, max-age=60";

    /** For bytes that a browser must confirm are current before reusing. */
    static final String REVALIDATE = "private, no-cache";

    private ResponseCaching() {
    }
}

package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.entity.TagCountEntity;
import io.github.md5sha256.realty.rest.json.TagResponse;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Serves {@code GET /v1/tags} -- every tag id in use, with its region count.
 *
 * <p>A consumer of {@code /v1/regions/search?tag=} otherwise has no way to discover
 * which values that filter accepts.</p>
 *
 * <p>Tags come back in id order, which is the order the tag listing already uses in
 * game; a caller wanting another one can sort a list this short itself.</p>
 */
public final class TagsHandler {

    private final RealtyBackend backend;

    public TagsHandler(@NotNull RealtyBackend backend) {
        this.backend = backend;
    }

    public void handle(@NotNull Context ctx) {
        // One statement for every tag. Listing the ids and counting each was a query
        // per tag, and the front page asks for this list on every visit.
        List<TagCountEntity> counts = this.backend.countRegionsPerTag();
        List<TagResponse> tags = new ArrayList<>(counts.size());
        for (TagCountEntity count : counts) {
            tags.add(new TagResponse(count.tagId(), count.regionCount()));
        }
        ctx.header("Cache-Control", ResponseCaching.SHORT_LIVED);
        ctx.json(tags);
    }
}

package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
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
 * <p>The backend's own ordering is preserved rather than re-sorted here: it is the
 * order the tag listing already uses in game, and a caller wanting another one can
 * sort a list this short itself.</p>
 */
public final class TagsHandler {

    private final RealtyBackend backend;

    public TagsHandler(@NotNull RealtyBackend backend) {
        this.backend = backend;
    }

    public void handle(@NotNull Context ctx) {
        List<String> tagIds = this.backend.getAllTagIds();
        List<TagResponse> tags = new ArrayList<>(tagIds.size());
        for (String tagId : tagIds) {
            tags.add(new TagResponse(tagId, this.backend.countRegionsByTag(tagId)));
        }
        ctx.json(tags);
    }
}

package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class RealtyTags {

    private final Map<String, ConfigRegionTag> tags;

    public RealtyTags(@NotNull RegionTagSettings settings) {
        Map<String, ConfigRegionTag> map = new LinkedHashMap<>();
        for (ConfigRegionTag tag : settings.tags()) {
            map.put(tag.tagId(), tag);
        }
        this.tags = Collections.unmodifiableMap(map);
    }

    public @NotNull Map<String, ConfigRegionTag> tags() {
        return this.tags;
    }

    public @Nullable ConfigRegionTag get(@NotNull String tagId) {
        return this.tags.get(tagId);
    }

    public @NotNull Set<String> tagIds() {
        return this.tags.keySet();
    }

    public @NotNull Collection<ConfigRegionTag> values() {
        return this.tags.values();
    }

}

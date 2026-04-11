package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.List;
import java.util.Objects;

@ConfigSerializable
public record RegionTagSettings(
        @Setting("tags") @NotNull List<ConfigRegionTag> tags
) {
    public RegionTagSettings(@Nullable List<ConfigRegionTag> tags) {
        this.tags = Objects.requireNonNullElse(tags, List.of());
    }
}

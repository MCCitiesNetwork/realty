package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.List;

@ConfigSerializable
public record RegionFlagSettings(
        @Setting @Nullable RegionFlagStates global,
        @Setting @Nullable List<GroupedRegionFlags> grouped
) {
}

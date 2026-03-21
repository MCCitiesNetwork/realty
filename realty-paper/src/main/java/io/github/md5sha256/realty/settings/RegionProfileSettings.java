package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.api.RegionState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.List;
import java.util.Map;

@ConfigSerializable
public record RegionProfileSettings(
        @Setting @Nullable Map<RegionState, RegionProfile> global,
        @Setting @Nullable List<GroupedRegionProfile> grouped
) {
}

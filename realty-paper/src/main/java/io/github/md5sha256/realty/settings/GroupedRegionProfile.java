package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.api.RegionState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.Map;
import java.util.Set;

@ConfigSerializable
public record GroupedRegionProfile(@Setting("regions") @Required @NotNull Set<String> regions,
                                   @Setting("states") @Required @NotNull Map<RegionState, RegionProfile> states) {
}

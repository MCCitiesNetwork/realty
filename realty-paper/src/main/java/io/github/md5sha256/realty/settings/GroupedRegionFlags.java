package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.Set;

@ConfigSerializable
public record GroupedRegionFlags(@Setting("regions") @Required @NotNull Set<String> regions,
                                 @Setting("states") @Required @NotNull RegionFlagStates states) {
}

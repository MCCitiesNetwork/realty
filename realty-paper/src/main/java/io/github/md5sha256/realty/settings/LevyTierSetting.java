package io.github.md5sha256.realty.settings;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/** One step of a levy rule in {@code fees.yml}: from this many plots, charge this percentage. */
@ConfigSerializable
public record LevyTierSetting(
        @Setting("from-plots") int fromPlots,
        @Setting("percent") double percent
) {}

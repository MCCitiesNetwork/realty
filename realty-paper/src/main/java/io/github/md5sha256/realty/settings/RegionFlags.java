package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.Map;

@ConfigSerializable
public record RegionFlags(@Setting("flags") @Required @NotNull Map<String, String> flags) {
}

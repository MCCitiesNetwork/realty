package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.api.SignProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.Map;

@ConfigSerializable
public record RegionProfile(
        @Setting("priority") @Nullable Integer priority,
        @Setting("flags") @Required @NotNull Map<String, String> flags,
        @Setting("sign") @Nullable SignProfile sign) {
}

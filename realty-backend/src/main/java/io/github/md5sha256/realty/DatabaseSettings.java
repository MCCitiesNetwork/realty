package io.github.md5sha256.realty;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public record DatabaseSettings(
        @Setting("url")
        @Required
        @NotNull String url,
        @Setting("username")
        @NotNull String username,
        @Setting("password")
        @NotNull String password
) {

}

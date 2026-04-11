package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public record TagPermission(
        @Setting("node") @Required @NotNull String node,
        @Setting("default") @Required @NotNull PermissionDefault permissionDefault
) {

    public enum PermissionDefault {
        OP,
        TRUE,
        FALSE
    }

}

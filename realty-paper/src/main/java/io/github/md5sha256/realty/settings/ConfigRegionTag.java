package io.github.md5sha256.realty.settings;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public record ConfigRegionTag(
        @Setting("tag-id") @Required @NotNull String tagId,
        @Setting("tag-display-name") @Required @NotNull Component tagDisplayName,
        @Setting("permission") @Required @NotNull String permission,
        @Setting("permission-default") @Required @NotNull PermissionDefault permissionDefault
) {

    public enum PermissionDefault {
        OP,
        TRUE,
        FALSE
    }

}

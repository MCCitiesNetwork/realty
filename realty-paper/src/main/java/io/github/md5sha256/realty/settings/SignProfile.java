package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.List;

@ConfigSerializable
public record SignProfile(
        @Setting("lines") @Required @NotNull List<String> lines,
        @Setting("right-click-commands") @Nullable List<String> rightClickCommands,
        @Setting("left-click-commands") @Nullable List<String> leftClickCommands
) {
}

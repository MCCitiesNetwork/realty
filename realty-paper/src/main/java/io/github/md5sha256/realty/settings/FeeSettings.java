package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.List;

/** The contents of {@code fees.yml}: the sale levies a server defines. */
@ConfigSerializable
public record FeeSettings(
        @Setting("levies") @Nullable List<LevySetting> levies
) {

    public FeeSettings {
        if (levies == null) {
            levies = List.of();
        }
    }

    public @NotNull List<LevySetting> levies() {
        return levies;
    }
}

package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.List;
import java.util.UUID;

@ConfigSerializable
public record TaxSettings(
        @Setting("enabled") boolean enabled,
        @Setting("exempt-uuids") @NotNull List<UUID> exemptUuids
) {

    public TaxSettings {
        if (exemptUuids == null) {
            exemptUuids = List.of();
        }
    }
}

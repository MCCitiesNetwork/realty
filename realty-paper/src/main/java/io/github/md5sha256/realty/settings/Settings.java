package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.api.RegionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.UUID;

@ConfigSerializable
public record Settings(
        @Setting("default-freehold-authority-uuid") @Required @NotNull UUID defaultFreeholdAuthority,
        @Setting("default-freehold-titleholder-uuid") @Nullable UUID defaultFreeholdTitleholder,
        @Setting("default-lease-authority-uuid") @Required @NotNull UUID defaultLeaseAuthority,
        @Setting("date-format") @Required @NotNull SimpleDateFormat dateFormat,
        @Setting("profile-reapply-per-tick") int profileReapplyPerTick,
        @Setting("subregion-min-volume") int subregionMinVolume
) {

    public Settings {
        if (profileReapplyPerTick <= 0) {
            profileReapplyPerTick = 10;
        }
        if (subregionMinVolume <= 0) {
            subregionMinVolume = 20;
        }
    }
}


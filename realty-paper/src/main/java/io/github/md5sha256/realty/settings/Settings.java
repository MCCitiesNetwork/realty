package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.api.RegionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ConfigSerializable
public record Settings(
        @Setting("default-freehold-authority-uuid") @Required @NotNull UUID defaultFreeholdAuthority,
        @Setting("default-freehold-titleholder-uuid") @Nullable UUID defaultFreeholdTitleholder,
        @Setting("default-leasehold-authority-uuid") @Required @NotNull UUID defaultLeaseholdAuthority,
        @Setting("date-format") @Required @NotNull SimpleDateFormat dateFormat,
        @Setting("profile-reapply-per-tick") int profileReapplyPerTick,
        @Setting("subregion-min-volume") int subregionMinVolume,
        @Setting("offer-payment-duration-seconds") long offerPaymentDurationSeconds,
        @Setting("lease-termination-notice-seconds") long terminationNoticeSeconds,
        @Setting("subregion-tag-blacklist") @NotNull List<String> subregionTagBlacklist,
        @Setting("subregion-wand-material") @NotNull String subregionWandMaterial,
        @Setting("teleportation-starting-height") int teleportStartHeight,
        @Setting("schematic-capture-cooldown-seconds") long schematicCaptureCooldownSeconds,
        @Setting("schematic-max-volume") long schematicMaxVolume,
        @Setting("schematic-capture-blocks-per-tick") int schematicCaptureBlocksPerTick
) {

    public Settings {
        if (profileReapplyPerTick <= 0) {
            profileReapplyPerTick = 10;
        }
        if (subregionMinVolume <= 0) {
            subregionMinVolume = 20;
        }
        if (offerPaymentDurationSeconds <= 0) {
            offerPaymentDurationSeconds = 86400;
        }
        if (terminationNoticeSeconds <= 0) {
            terminationNoticeSeconds = 604800;
        }
        if (subregionTagBlacklist == null) {
            subregionTagBlacklist = List.of();
        }
        if (subregionWandMaterial == null || subregionWandMaterial.isBlank()) {
            subregionWandMaterial = "GOLDEN_AXE";
        }
        // Zero is left alone for the cooldown and the volume cap: it is how an
        // operator disables each. Only a negative is nonsense and corrected.
        if (schematicCaptureCooldownSeconds < 0) {
            schematicCaptureCooldownSeconds = 3600;
        }
        if (schematicMaxVolume < 0) {
            schematicMaxVolume = 1_000_000L;
        }
        // A zero budget would mean a copy that never advances, so unlike the two
        // above this is corrected the way profileReapplyPerTick is.
        if (schematicCaptureBlocksPerTick <= 0) {
            schematicCaptureBlocksPerTick = 20_000;
        }
    }
}


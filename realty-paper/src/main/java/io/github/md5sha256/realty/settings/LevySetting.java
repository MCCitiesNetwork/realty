package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.PartyKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;

/**
 * One levy of {@code fees.yml}.
 *
 * @param recipient the raw node: omitted or the string {@code authority} for the plot's
 *                  authority, or a map with {@code name}, {@code uuid} and {@code type} like a
 *                  party of {@code settings.yml}. {@link io.github.md5sha256.realty.fees.ConfiguredLevyCatalog}
 *                  interprets it.
 */
@ConfigSerializable
public record LevySetting(
        @Setting("name") @Nullable String name,
        @Setting("enabled") boolean enabled,
        @Setting("default") boolean isDefault,
        @Setting("paid-by") @Nullable FeeSide paidBy,
        @Setting("recipient") @Nullable ConfigurationNode recipient,
        @Setting("authority-kind") @Nullable PartyKind authorityKind,
        @Setting("rules") @Nullable List<LevyRuleSetting> rules
) {

    public LevySetting {
        if (rules == null) {
            rules = List.of();
        }
    }

    public @NotNull List<LevyRuleSetting> rules() {
        return rules;
    }
}

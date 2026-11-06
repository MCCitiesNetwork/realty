package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.List;

/**
 * One rule of a levy in {@code fees.yml}: a tag predicate and the tiers that apply when it
 * matches. An omitted {@code match} matches every plot.
 */
@ConfigSerializable
public record LevyRuleSetting(
        @Setting("match") @Nullable TagMatch match,
        @Setting("tiers") @Nullable List<LevyTierSetting> tiers
) {

    public LevyRuleSetting {
        if (match == null) {
            match = new TagMatch(null, null);
        }
        if (tiers == null) {
            tiers = List.of();
        }
    }

    public @NotNull TagMatch match() {
        return match;
    }

    public @NotNull List<LevyTierSetting> tiers() {
        return tiers;
    }
}

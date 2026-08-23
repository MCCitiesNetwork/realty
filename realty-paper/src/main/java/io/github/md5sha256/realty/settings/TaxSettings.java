package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.List;
import java.util.UUID;

/**
 * Property-tax configuration. The whole tax is the {@code rules} list: each rule
 * pairs a tag predicate with the formula charged to the plots it matches, so there
 * is no tax at all beyond what the rules define. A server-wide tax is a rule with
 * no {@code match}.
 */
@ConfigSerializable
public record TaxSettings(
        @Setting("enabled") boolean enabled,
        @Setting("government-account") @NotNull String governmentAccount,
        @Setting("exempt-uuids") @NotNull List<UUID> exemptUuids,
        @Setting("rules") @Nullable List<TaxRule> rules
) {

    public TaxSettings {
        if (governmentAccount == null || governmentAccount.isBlank()) {
            governmentAccount = "DCGovernment";
        }
        if (exemptUuids == null) {
            exemptUuids = List.of();
        }
        if (rules == null) {
            rules = List.of();
        }
    }

    /** The tax brackets, in precedence order. Empty means no property tax. */
    public @NotNull List<TaxRule> rules() {
        return rules;
    }
}

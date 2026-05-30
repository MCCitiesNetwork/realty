package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.List;
import java.util.UUID;

@ConfigSerializable
public record TaxSettings(
        @Setting("enabled") boolean enabled,
        @Setting("government-account") @NotNull String governmentAccount,
        @Setting("exempt-uuids") @NotNull List<UUID> exemptUuids,
        @Setting("exempt-plot-threshold") int exemptPlotThreshold,
        @Setting("rules") @NotNull List<TaxRule> rules,
        @Setting("default-formula") @NotNull String defaultFormula
) {

    /** Built-in fallback formula — the pre-tag-rules behaviour. */
    public static final String DEFAULT_FORMULA = "2.5 * <plots>^2 - 6 * <plots>";

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
        if (defaultFormula == null || defaultFormula.isBlank()) {
            defaultFormula = DEFAULT_FORMULA;
        }
    }
}

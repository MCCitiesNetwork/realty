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

    /** Built-in default — the Taxation Act's federal property-tax formula. Gives an
     * owner's total daily tax as a function of their plot count {@code <plots>};
     * evaluated once per owner (not per plot). Owners of 7 or fewer plots are exempt
     * via {@code exempt-plot-threshold}, and the result is rounded down to the cent. */
    public static final String DEFAULT_FORMULA = "0.25 * 1.16^<plots> + 0.3 * <plots>^2 + 2.5 * <plots> - 25";

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

package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.PlotOwnerCount;
import io.github.md5sha256.realty.settings.TaxSettings;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.event.TaxCycleEvent;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import net.democracycraft.treasury.model.tax.TaxCollection;
import net.democracycraft.treasury.model.tax.TaxCycleType;
import net.democracycraft.treasury.model.tax.TaxResult;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class PropertyTaxListener implements Listener {

    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);
    private static final String PLUGIN_SYSTEM = "realty";
    private static final String TAX_TYPE = "property_tax";

    private final Database database;
    private final TreasuryApi treasuryApi;
    private final AtomicReference<TaxSettings> taxSettings;
    private final Logger logger;

    public PropertyTaxListener(
            @NotNull Database database,
            @NotNull TreasuryApi treasuryApi,
            @NotNull AtomicReference<TaxSettings> taxSettings,
            @NotNull Logger logger
    ) {
        this.database = database;
        this.treasuryApi = treasuryApi;
        this.taxSettings = taxSettings;
        this.logger = logger;
    }

    @EventHandler
    public void onTaxCycle(@NotNull TaxCycleEvent event) {
        if (event.getCycleType() != TaxCycleType.DAILY) {
            return;
        }
        TaxSettings settings = taxSettings.get();
        if (!settings.enabled()) {
            return;
        }

        List<PlotOwnerCount> plotCounts;
        try (SqlSessionWrapper session = database.openSession(true)) {
            plotCounts = session.freeholdContractMapper().selectPlotCountsByTitleHolder();
        } catch (Exception e) {
            logger.severe("Failed to load plot counts for property tax collection: " + e.getMessage());
            return;
        }

        Set<UUID> exempt = new HashSet<>(settings.exemptUuids());
        Instant periodStart = event.getPeriodStart();

        List<TaxCollection> collections = new ArrayList<>();
        for (PlotOwnerCount entry : plotCounts) {
            UUID owner = entry.titleHolderId();
            int plots = entry.plotCount();

            if (plots <= 3) {
                continue;
            }
            if (exempt.contains(owner)) {
                continue;
            }

            int accountId = resolvePersonalAccountId(owner);
            if (accountId == -1) {
                logger.warning("No Treasury account found for plot owner " + owner + ", skipping property tax");
                continue;
            }

            BigDecimal taxAmount = computePropertyTax(plots);
            byte[] dedupKey = ("realty:property_tax:" + owner + ":" + periodStart.toEpochMilli())
                    .getBytes(StandardCharsets.UTF_8);

            collections.add(TaxCollection.toDefaultAccount(
                    accountId,
                    taxAmount,
                    TAX_TYPE,
                    "Daily property tax (" + plots + " plots)",
                    SYSTEM_UUID,
                    PLUGIN_SYSTEM,
                    dedupKey
            ));
        }

        if (collections.isEmpty()) {
            return;
        }

        List<TaxResult> results = event.getTaxApi().collectBatch(collections);

        long collected = 0;
        long skipped = 0;
        long failed = 0;
        for (TaxResult result : results) {
            if (result.isSuccess()) {
                collected++;
            } else if (result.wasSkipped()) {
                skipped++;
            } else {
                failed++;
                if (result instanceof TaxResult.Failed f) {
                    logger.warning("Property tax collection failure: " + f.errorMessage());
                }
            }
        }
        logger.info("Daily property tax cycle: " + collected + " collected, " + skipped + " skipped, " + failed + " failed");
    }

    // y = 2.5 * x^2 - 6 * x, rounded to 2 decimal places
    private static @NotNull BigDecimal computePropertyTax(int plots) {
        double raw = 2.5 * ((double) plots * plots) - 6.0 * plots;
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }

    private int resolvePersonalAccountId(@NotNull UUID owner) {
        List<Account> accounts = treasuryApi.getAccountsByOwner(owner);
        if (accounts.isEmpty()) {
            return -1;
        }
        return accounts.stream()
                .filter(a -> a.getAccountType() == AccountType.PERSONAL)
                .findFirst()
                .orElse(accounts.get(0))
                .getAccountId();
    }
}

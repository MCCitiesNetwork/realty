package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.TitleHeldRegionTag;
import io.github.md5sha256.realty.settings.TaxSettings;
import io.github.md5sha256.realty.tax.PropertyTaxPolicy;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.event.TaxCycleEvent;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import net.democracycraft.treasury.model.tax.TaxCollection;
import net.democracycraft.treasury.model.tax.TaxCycleType;
import net.democracycraft.treasury.model.tax.TaxResult;
import net.democracycraft.treasury.utils.Idempotency;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        // Compile the tag-matched formula ruleset for this cycle.
        PropertyTaxPolicy policy = PropertyTaxPolicy.compile(settings, logger);

        // Enumerate every title-held region with its tags, then fold into
        // owner -> (region -> tag set).
        Map<UUID, Map<String, Set<String>>> regionsByOwner = new HashMap<>();
        try (SqlSessionWrapper session = database.openSession(true)) {
            for (TitleHeldRegionTag row : session.freeholdContractMapper().selectTitleHeldRegionTags()) {
                Map<String, Set<String>> regions =
                        regionsByOwner.computeIfAbsent(row.titleHolderId(), k -> new HashMap<>());
                Set<String> tags = regions.computeIfAbsent(row.worldGuardRegionId(), k -> new HashSet<>());
                if (row.tagId() != null) {
                    tags.add(row.tagId());
                }
            }
        } catch (Exception e) {
            logger.severe("Failed to load title-held regions for property tax collection: " + e.getMessage());
            return;
        }

        Set<UUID> exempt = new HashSet<>(settings.exemptUuids());
        int threshold = settings.exemptPlotThreshold();
        Instant periodStart = event.getPeriodStart();

        // Resolve the configured destination account once for the whole batch.
        Integer destinationAccountId = resolveDestinationAccountId(settings.governmentAccount());

        List<TaxCollection> collections = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, Set<String>>> ownerEntry : regionsByOwner.entrySet()) {
            UUID owner = ownerEntry.getKey();
            Map<String, Set<String>> regions = ownerEntry.getValue();
            int plots = regions.size();

            if (plots <= threshold) {
                continue;
            }
            if (exempt.contains(owner)) {
                continue;
            }

            // Sum the per-property, tag-matched tax. <plots> = the owner's total plot count.
            BigDecimal taxAmount = BigDecimal.ZERO;
            for (Set<String> regionTags : regions.values()) {
                taxAmount = taxAmount.add(policy.taxForRegion(regionTags, plots));
            }
            if (taxAmount.signum() <= 0) {
                continue;
            }

            int accountId = resolvePersonalAccountId(owner);
            if (accountId == -1) {
                logger.warning("No Treasury account found for plot owner " + owner + ", skipping property tax");
                continue;
            }

            byte[] dedupKey = Idempotency.sha256("realty:property_tax:" + owner + ":" + periodStart.toEpochMilli());
            String memo = "Daily property tax (" + plots + " plots)";

            TaxCollection collection = destinationAccountId != null
                    ? TaxCollection.toAccount(accountId, destinationAccountId, taxAmount, TAX_TYPE,
                            memo, SYSTEM_UUID, PLUGIN_SYSTEM, dedupKey)
                    : TaxCollection.toDefaultAccount(accountId, taxAmount, TAX_TYPE,
                            memo, SYSTEM_UUID, PLUGIN_SYSTEM, dedupKey);

            collections.add(collection);
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

    /**
     * Resolves the configured government account name to an account ID.
     * Returns {@code null} if the account is not found, causing the collection
     * to fall back to Treasury's default tax account.
     */
    private @org.jetbrains.annotations.Nullable Integer resolveDestinationAccountId(@NotNull String accountName) {
        Account account = treasuryApi.getGovernmentAccountByName(accountName);
        if (account == null) {
            logger.warning("Configured property tax destination '" + accountName
                    + "' not found in Treasury — falling back to Treasury's default tax account");
            return null;
        }
        return account.getAccountId();
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

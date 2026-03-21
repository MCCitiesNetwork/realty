package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty buy <region>}.
 *
 * <p>Performs a fixed-price purchase at the listed price without requiring
 * approval from the current title holder.</p>
 *
 * <p>Permission: {@code realty.command.buy}.</p>
 */
public record BuyCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull Economy economy,
        @NotNull NotificationService notificationService,
        @NotNull RegionProfileService regionProfileService,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("buy")
                .permission("realty.command.buy")
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor("error.no-region"));
            return;
        }
        String regionId = region.region().getId();
        // Step 1: validate eligibility and get price (async)
        CompletableFuture.supplyAsync(() -> {
            try {
                return logic.validateBuy(regionId, region.world().getUID(), sender.getUniqueId());
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("buy.error",
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(validation -> {
            if (validation == null) {
                return;
            }
            switch (validation) {
                case RealtyLogicImpl.BuyValidation.Eligible eligible ->
                        handlePaymentAndTransfer(sender, region, regionId, eligible);
                case RealtyLogicImpl.BuyValidation.NoFreeholdContract ignored ->
                        sender.sendMessage(messages.messageFor("buy.no-freehold-contract",
                                Placeholder.unparsed("region", regionId)));
                case RealtyLogicImpl.BuyValidation.NotForFreehold ignored ->
                        sender.sendMessage(messages.messageFor("buy.not-for-sale",
                                Placeholder.unparsed("region", regionId)));
                case RealtyLogicImpl.BuyValidation.IsAuthority ignored ->
                        sender.sendMessage(messages.messageFor("buy.is-authority"));
                case RealtyLogicImpl.BuyValidation.IsTitleHolder ignored ->
                        sender.sendMessage(messages.messageFor("buy.is-title-holder"));
            }
        }, executorState.mainThreadExec());
    }

    private void handlePaymentAndTransfer(@NotNull Player sender,
                                          @NotNull WorldGuardRegion region,
                                          @NotNull String regionId,
                                          @NotNull RealtyLogicImpl.BuyValidation.Eligible eligible) {
        double price = eligible.price();
        // Step 2: economy withdrawal (main thread)
        double balance = economy.getBalance(sender);
        if (balance < price) {
            sender.sendMessage(messages.messageFor("buy.insufficient-funds",
                    Placeholder.unparsed("price", String.valueOf(price)),
                    Placeholder.unparsed("balance", String.valueOf(balance))));
            return;
        }
        EconomyResponse response = economy.withdrawPlayer(sender, price);
        if (!response.transactionSuccess()) {
            sender.sendMessage(messages.messageFor("buy.payment-failed",
                    Placeholder.unparsed("error", response.errorMessage)));
            return;
        }
        // Step 3: execute DB transfer (async)
        CompletableFuture.supplyAsync(() -> {
            try {
                RealtyLogicImpl.BuyResult result = logic.executeBuy(regionId, region.world().getUID(), sender.getUniqueId());
                Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, region.world().getUID());
                return Map.entry(result, placeholders);
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("buy.error",
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(entry -> {
            // Step 4: finalize on main thread
            if (entry == null || !(entry.getKey() instanceof RealtyLogicImpl.BuyResult.Success success)) {
                // Transfer failed — refund
                economy.depositPlayer(sender, price);
                sender.sendMessage(messages.messageFor("buy.transfer-failed",
                        Placeholder.unparsed("region", regionId)));
                return;
            }
            OfflinePlayer authority = Bukkit.getOfflinePlayer(success.authorityId());
            economy.depositPlayer(authority, price);
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(region.world()));
            if (regionManager != null) {
                ProtectedRegion protectedRegion = region.region();
                protectedRegion.getOwners().clear();
                protectedRegion.getOwners().addPlayer(sender.getUniqueId());
                protectedRegion.getMembers().clear();
            }
            regionProfileService.applyFlags(region, RegionState.SOLD, entry.getValue());
            sender.sendMessage(messages.messageFor("buy.success",
                    Placeholder.unparsed("price", String.valueOf(price)),
                    Placeholder.unparsed("region", regionId)));
            if (success.titleHolderId() != null) {
                notificationService.queueNotification(success.titleHolderId(),
                        messages.messageFor("notification.region-bought",
                                Placeholder.unparsed("player", sender.getName()),
                                Placeholder.unparsed("price", String.valueOf(price)),
                                Placeholder.unparsed("region", regionId)));
            }
        }, executorState.mainThreadExec());
    }

}

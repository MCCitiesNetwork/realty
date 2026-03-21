package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Groups all unset-related subcommands under {@code /realty unset}.
 *
 * <ul>
 *   <li>{@code /realty unset price [region]} — clear sale price</li>
 *   <li>{@code /realty unset titleholder [region]} — clear sale title holder</li>
 *   <li>{@code /realty unset tenant [region]} — clear lease tenant</li>
 * </ul>
 */
public record UnsetCommandGroup(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull CommandManager<CommandSourceStack> manager) {
        var base = manager.commandBuilder("realty")
                .literal("unset");
        return List.of(
                base.literal("price")
                        .permission("realty.command.unset.price")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeUnsetPrice)
                        .build(),
                base.literal("titleholder")
                        .permission("realty.command.unset.titleholder")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeUnsetTitleHolder)
                        .build(),
                base.literal("tenant")
                        .permission("realty.command.unset.tenant")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeUnsetTenant)
                        .build()
        );
    }

    private void executeUnsetPrice(@NotNull CommandContext<CommandSourceStack> ctx) {
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
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.UnsetPriceResult result = logic.unsetPrice(
                        regionId, region.world().getUID());
                switch (result) {
                    case RealtyLogicImpl.UnsetPriceResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("unset-price.success",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.NoSaleContract ignored ->
                            sender.sendMessage(messages.messageFor("unset-price.no-sale-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.OfferPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor("unset-price.offer-payment-in-progress",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.BidPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor("unset-price.bid-payment-in-progress",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor("unset-price.update-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("unset-price.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeUnsetTitleHolder(@NotNull CommandContext<CommandSourceStack> ctx) {
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
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.SetTitleHolderResult result = logic.setTitleHolder(
                        regionId, region.world().getUID(), null);
                switch (result) {
                    case RealtyLogicImpl.SetTitleHolderResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("unset-titleholder.success",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetTitleHolderResult.NoSaleContract ignored ->
                            sender.sendMessage(messages.messageFor("unset-titleholder.no-sale-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetTitleHolderResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor("unset-titleholder.update-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("unset-titleholder.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeUnsetTenant(@NotNull CommandContext<CommandSourceStack> ctx) {
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
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.SetTenantResult result = logic.setTenant(
                        regionId, region.world().getUID(), null);
                switch (result) {
                    case RealtyLogicImpl.SetTenantResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("unset-tenant.success",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetTenantResult.NoLeaseContract ignored ->
                            sender.sendMessage(messages.messageFor("unset-tenant.no-lease-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetTenantResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor("unset-tenant.update-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("unset-tenant.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}

package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Groups all unset-related subcommands under {@code /realty unset}.
 *
 * <ul>
 *   <li>{@code /realty unset price [region]} — clear freehold price</li>
 *   <li>{@code /realty unset titleholder [region]} — clear freehold title holder</li>
 *   <li>{@code /realty unset tenant [region]} — clear leasehold tenant</li>
 * </ul>
 */
public record UnsetCommandGroup(
        @NotNull RealtyPaperApi api,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder
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

    private void executeUnsetPrice(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        if (sender instanceof Player player
                && !sender.hasPermission("realty.command.unset.price.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_NO_PERMISSION));
            return;
        }
        api.unsetPrice(regionId, worldId).thenAccept(result -> {
            switch (result) {
                case RealtyBackend.UnsetPriceResult.Success ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_SUCCESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.UnsetPriceResult.NoFreeholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_NO_FREEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.UnsetPriceResult.OfferPaymentInProgress ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_OFFER_PAYMENT_IN_PROGRESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.UnsetPriceResult.BidPaymentInProgress ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_BID_PAYMENT_IN_PROGRESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.UnsetPriceResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
            }
        });
    }

    private void executeUnsetTitleHolder(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        if (sender instanceof Player player
                && !sender.hasPermission("realty.command.unset.titleholder.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_NO_PERMISSION));
            return;
        }
        api.setTitleHolder(region, null).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.SetTitleHolderResult.Success ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_SUCCESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTitleHolderResult.NoFreeholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_NO_FREEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTitleHolderResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTitleHolderResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }

    private void executeUnsetTenant(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        if (sender instanceof Player player
                && !sender.hasPermission("realty.command.unset.tenant.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_NO_PERMISSION));
            return;
        }
        api.setTenant(region, null).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.SetTenantResult.Success ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_SUCCESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTenantResult.NoLeaseholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTenantResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTenantResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }

}

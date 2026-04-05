package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.incendo.cloud.paper.util.sender.Source;

import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

/**
 * Handles {@code /realty buy <region>}.
 *
 * <p>Performs a fixed-price purchase at the listed price without requiring
 * approval from the current title holder.</p>
 *
 * <p>Permission: {@code realty.command.buy}.</p>
 */
public record BuyCommand(
        @NotNull RealtyPaperApi api,
        @NotNull NotificationService notificationService,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("buy")
                .permission("realty.command.buy")
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        api.buy(region, sender.getUniqueId()).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.BuyResult.Success success -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.BUY_SUCCESS,
                            Placeholder.unparsed("price", CurrencyFormatter.format(success.price())),
                            Placeholder.unparsed("region", success.regionId())));
                    if (success.previousTitleHolderId() != null) {
                        notificationService.queueNotification(success.previousTitleHolderId(),
                                messages.messageFor(MessageKeys.NOTIFICATION_REGION_BOUGHT,
                                        Placeholder.unparsed("player", sender.getName()),
                                        Placeholder.unparsed("price", CurrencyFormatter.format(success.price())),
                                        Placeholder.unparsed("region", success.regionId())));
                    }
                }
                case RealtyPaperApi.BuyResult.NoFreeholdContract noContract ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_NO_FREEHOLD_CONTRACT,
                                Placeholder.unparsed("region", noContract.regionId())));
                case RealtyPaperApi.BuyResult.NotForSale notForSale ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_NOT_FOR_SALE,
                                Placeholder.unparsed("region", notForSale.regionId())));
                case RealtyPaperApi.BuyResult.IsAuthority ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_IS_AUTHORITY));
                case RealtyPaperApi.BuyResult.IsTitleHolder ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_IS_TITLE_HOLDER));
                case RealtyPaperApi.BuyResult.InsufficientFunds insufficient ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_INSUFFICIENT_FUNDS,
                                Placeholder.unparsed("price", CurrencyFormatter.format(insufficient.price())),
                                Placeholder.unparsed("balance", CurrencyFormatter.format(insufficient.balance()))));
                case RealtyPaperApi.BuyResult.PaymentFailed failed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_PAYMENT_FAILED,
                                Placeholder.unparsed("error", failed.error())));
                case RealtyPaperApi.BuyResult.TransferFailed transferFailed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_TRANSFER_FAILED,
                                Placeholder.unparsed("region", transferFailed.regionId())));
                case RealtyPaperApi.BuyResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }

}

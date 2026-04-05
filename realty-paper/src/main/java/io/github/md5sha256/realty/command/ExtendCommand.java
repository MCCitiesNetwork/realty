package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
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
 * Handles {@code /realty extend <region>}.
 *
 * <p>Permission: {@code realty.command.extend}.</p>
 */
public record ExtendCommand(
        @NotNull RealtyPaperApi api,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("extend")
                .permission("realty.command.extend")
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
        api.extend(region, sender.getUniqueId()).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.ExtendResult.Success success ->
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_SUCCESS,
                                Placeholder.unparsed("region", success.regionId()),
                                Placeholder.unparsed("price", CurrencyFormatter.format(success.price()))));
                case RealtyPaperApi.ExtendResult.NoLeaseholdContract noContract ->
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", noContract.regionId())));
                case RealtyPaperApi.ExtendResult.NoExtensionsRemaining noExtensions ->
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_NO_EXTENSIONS,
                                Placeholder.unparsed("region", noExtensions.regionId())));
                case RealtyPaperApi.ExtendResult.InsufficientFunds insufficient ->
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_INSUFFICIENT_FUNDS,
                                Placeholder.unparsed("price", CurrencyFormatter.format(insufficient.price())),
                                Placeholder.unparsed("balance", CurrencyFormatter.format(insufficient.balance()))));
                case RealtyPaperApi.ExtendResult.PaymentFailed failed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_PAYMENT_FAILED,
                                Placeholder.unparsed("error", failed.error())));
                case RealtyPaperApi.ExtendResult.UpdateFailed updateFailed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_UPDATE_FAILED,
                                Placeholder.unparsed("region", updateFailed.regionId())));
                case RealtyPaperApi.ExtendResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }

}

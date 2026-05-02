package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Handles {@code /realty transfer <titleholder> [region]}.
 *
 * <p>Transfers freehold ownership to the given player and clears the listed price.
 * Equivalent to {@code /realty set titleholder} followed by clearing the price atomically.</p>
 *
 * <p>Permission: {@code realty.command.transfer} (or {@code realty.command.transfer.others}
 * to transfer regions you don't own).</p>
 */
public record TransferCommand(
        @NotNull RealtyPaperApi api,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name != null ? name : uuid.toString();
    }

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("transfer")
                .permission("realty.command.transfer")
                .required("titleholder", AuthorityParser.authority())
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        UUID titleHolderId = ctx.get("titleholder");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        if (sender instanceof Player player
                && !sender.hasPermission("realty.command.transfer.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.TRANSFER_NO_PERMISSION));
            return;
        }
        api.transferTitleHolder(region, titleHolderId).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.SetTitleHolderResult.Success success ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TRANSFER_SUCCESS,
                                Placeholder.unparsed("titleholder", resolveName(titleHolderId)),
                                Placeholder.unparsed("region", success.regionId())));
                case RealtyPaperApi.SetTitleHolderResult.NoFreeholdContract noContract ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TRANSFER_NO_FREEHOLD_CONTRACT,
                                Placeholder.unparsed("region", noContract.regionId())));
                case RealtyPaperApi.SetTitleHolderResult.UpdateFailed updateFailed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TRANSFER_UPDATE_FAILED,
                                Placeholder.unparsed("region", updateFailed.regionId())));
                case RealtyPaperApi.SetTitleHolderResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TRANSFER_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }
}

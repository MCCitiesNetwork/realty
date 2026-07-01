package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.BooleanParser;
import org.jetbrains.annotations.NotNull;

/**
 * Handles {@code /realty rentable <true|false> [region]}.
 *
 * <p>Toggles whether a leasehold accepts new tenants. When set to {@code false} the region cannot be
 * rented even while vacant — useful after {@code /realty terminate} to stop the region being re-rented
 * once the current lease ends. It never affects the sitting tenant. Permission:
 * {@code realty.command.rentable} (with {@code realty.command.rentable.others} as the admin override).</p>
 */
public record RentableCommand(
        @NotNull RealtyPaperApi api,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("rentable")
                .permission("realty.command.rentable")
                .required("accepting", BooleanParser.booleanParser())
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        boolean accepting = ctx.get("accepting");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        boolean bypass = sender.hasPermission("realty.command.rentable.others");
        String regionId = region.region().getId();
        api.setRentable(regionId, region.world().getUID(), sender.getUniqueId(), bypass, accepting)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyBackend.SetRentableResult.Success success ->
                                sender.sendMessage(messages.messageFor(success.acceptingTenants()
                                                ? MessageKeys.RENTABLE_ENABLED : MessageKeys.RENTABLE_DISABLED,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.SetRentableResult.NoChange noChange ->
                                sender.sendMessage(messages.messageFor(noChange.acceptingTenants()
                                                ? MessageKeys.RENTABLE_ALREADY_ENABLED
                                                : MessageKeys.RENTABLE_ALREADY_DISABLED,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.SetRentableResult.NoLeaseholdContract ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.RENTABLE_NO_LEASEHOLD_CONTRACT,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.SetRentableResult.NotAuthorized ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.RENTABLE_NOT_LANDLORD,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.SetRentableResult.UpdateFailed ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.RENTABLE_UPDATE_FAILED,
                                        Placeholder.unparsed("region", regionId)));
                    }
                }).exceptionally(ex -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.RENTABLE_ERROR,
                            Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
                    return null;
                });
    }
}

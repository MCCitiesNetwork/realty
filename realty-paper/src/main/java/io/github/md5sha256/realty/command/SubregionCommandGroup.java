package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.subregion.PlayerSubregioningService;
import io.github.md5sha256.realty.subregion.PlayerSubregioningState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record SubregionCommandGroup(
        @NotNull PlayerSubregioningService subregioningService,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(
            @NotNull Command.Builder<CommandSourceStack> builder) {
        var base = builder.literal("subregion");
        return List.of(
                base.literal("start")
                        .permission("realty.command.subregion.start")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeStart)
                        .build(),
                base.literal("apply")
                        .permission("realty.command.subregion.apply")
                        .handler(this::executeApply)
                        .build(),
                base.literal("cancel")
                        .permission("realty.command.subregion.cancel")
                        .handler(this::executeCancel)
                        .build()
        );
    }

    private void executeStart(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player player)) {
            ctx.sender().getSender().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(player.getLocation()));
        if (region == null) {
            player.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        subregioningService.startSubregioning(player, region);
        player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_START_SUCCESS,
                Placeholder.unparsed("region", region.region().getId())));
    }

    private void executeApply(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player player)) {
            ctx.sender().getSender().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        PlayerSubregioningState state = subregioningService.getState(player.getUniqueId());
        if (state == null) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_NOT_ACTIVE));
            return;
        }
        PlayerSubregioningState.SelectionResult result = state.tryApplySelection();
        switch (result) {
            case PlayerSubregioningState.SelectionResult.Success success ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_APPLY_SUCCESS,
                            Placeholder.unparsed("region", state.parentRegion().region().getId())));
            case PlayerSubregioningState.SelectionResult.WrongWorld ignored ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_WRONG_WORLD));
            case PlayerSubregioningState.SelectionResult.IncompleteSelection ignored ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_INCOMPLETE_SELECTION));
            case PlayerSubregioningState.SelectionResult.ExceedsParentBounds ignored ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_EXCEEDS_BOUNDS,
                            Placeholder.unparsed("region", state.parentRegion().region().getId())));
            case PlayerSubregioningState.SelectionResult.NoRegionManager ignored ->
                    player.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                            Placeholder.unparsed("error", "Region manager unavailable")));
            case PlayerSubregioningState.SelectionResult.OverlapsSibling overlap ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_OVERLAPS_SIBLING,
                            Placeholder.unparsed("sibling", overlap.sibling().getId())));
        }
    }

    private void executeCancel(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player player)) {
            ctx.sender().getSender().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        PlayerSubregioningState removed = subregioningService.removeState(player.getUniqueId());
        if (removed == null) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_NOT_ACTIVE));
            return;
        }
        player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_CANCEL_SUCCESS));
    }

}

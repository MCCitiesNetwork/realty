package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.wand.SubregionWand;
import io.github.md5sha256.realty.wand.SubregionWandManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Registers the player-facing subregion commands:
 * <ul>
 *     <li>{@code /realty subregion wand} — hands out the selection wand.</li>
 *     <li>{@code /realty subregion clear} — discards the current selection.</li>
 *     <li>{@code /realty subregion confirm} — opens the height dialog for the marked footprint,
 *     then the creation dialog.</li>
 * </ul>
 *
 * <p>Selection geometry, parent auto-detection and validation live in
 * {@link SubregionDialog} and {@code SubregionSelectionValidator}.</p>
 */
public record SubregionCommandGroup(
        @NotNull SubregionWand wand,
        @NotNull SubregionWandManager wandManager,
        @NotNull SubregionDialog dialog,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<? extends Source>> commands(
            @NotNull Command.Builder<Source> builder) {
        var base = builder.literal("subregion");
        return List.of(
                base.literal("wand")
                        .permission("realty.command.subregion.wand")
                        .handler(this::executeWand)
                        .build(),
                base.literal("clear")
                        .permission("realty.command.subregion.wand")
                        .handler(this::executeClear)
                        .build(),
                base.literal("confirm")
                        .permission("realty.command.subregion.confirm")
                        .handler(this::executeConfirm)
                        .build()
        );
    }

    private void executeWand(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        ItemStack item = wand.createWand();
        player.getInventory().addItem(item).forEach((index, leftover) ->
                player.getWorld().dropItem(player.getLocation(), leftover));
        player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_WAND_GIVEN));
    }

    private void executeClear(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        if (wandManager.get(player.getUniqueId()) == null) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_NOTHING_TO_CLEAR));
            return;
        }
        wandManager.clear(player.getUniqueId());
        player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_SELECTION_CLEARED));
    }

    private void executeConfirm(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        dialog.openHeight(player);
    }
}

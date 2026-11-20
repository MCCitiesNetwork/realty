package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.regions.Region;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.command.util.WorldEditSelections;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty redefine <region>}: gives a registered region the shape of the player's
 * WorldEdit selection.
 *
 * <p>{@code realty.command.redefine} is the admin gate, so the command acts with the bypass: any
 * registered region, rented or not. The shape rules still apply. There is no confirmation step, as
 * {@code /rg redefine} has none; the reply names the old and new size. The API fires the redefine
 * events; this command replies and notifies the tenant or title holder.</p>
 */
public record RedefineCommand(
        @NotNull RealtyPaperApi api,
        @NotNull ActorContexts actors,
        @NotNull ExecutorState executorState,
        @NotNull MessageContainer messages,
        @NotNull RedefineReplies replies
) implements CustomCommandBean.Single {

    private static final String PERMISSION = "realty.command.redefine";

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("redefine")
                .permission(PERMISSION)
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(this.messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.get("region");
        Region selection = WorldEditSelections.of(player);
        if (selection == null) {
            player.sendMessage(this.messages.messageFor(MessageKeys.REDEFINE_NO_SELECTION));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture
                .supplyAsync(() -> this.actors.forRegion(player, true, region), this.executorState.dbExec())
                .thenComposeAsync(actor -> this.api.redefineRegion(region, selection, actor),
                        this.executorState.mainThreadExec())
                .thenAcceptAsync(result -> this.replies.report(player, Optional.of(player.getUniqueId()), regionId,
                        result), this.executorState.mainThreadExec())
                .exceptionally(ex -> {
                    this.replies.error(player, ex);
                    return null;
                });
    }
}

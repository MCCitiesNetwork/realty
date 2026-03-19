package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles {@code /realty createsale <price> [--titleholder <name>] <region>}.
 *
 * <p>Permission: {@code realty.command.createsale}.</p>
 */
public record CreateSaleCommand(@NotNull ExecutorState executorState,
                                @NotNull RealtyLogicImpl logic,
                                @NotNull AtomicReference<Settings> settings,
                                @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    private static final CloudKey<Double> PRICE = CloudKey.of("price", Double.class);
    private static final CloudKey<WorldGuardRegion> REGION = CloudKey.of("region",
            WorldGuardRegion.class);
    private static final CommandFlag<UUID> TITLEHOLDER_FLAG =
            CommandFlag.<CommandSourceStack>builder("titleholder")
                    .withComponent(AuthorityParser.authority())
                    .build();

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("createsale")
                .permission("realty.command.createsale")
                .required(PRICE, DoubleParser.doubleParser(0))
                .flag(TITLEHOLDER_FLAG)
                .required(REGION, WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player player)) {
            return;
        }
        double price = ctx.get(PRICE);
        UUID authority = ctx.flags()
                .getValue(TITLEHOLDER_FLAG, settings.get().defaultSaleTitleholder());
        WorldGuardRegion region = ctx.get(REGION);
        UUID titleHolder = player.getUniqueId();
        CompletableFuture.supplyAsync(() -> {
            try {
                return logic.createSale(
                        region.region().getId(), region.world().getUID(),
                        price, authority, titleHolder);
            } catch (PersistenceException ex) {
                throw new CompletionException(ex);
            }
        }, executorState.dbExec()).thenAcceptAsync(created -> {
            if (created) {
                region.region().getMembers().addPlayer(authority);
                sender.sendMessage(messages.messageFor("create-sale.success"));
            } else {
                sender.sendMessage(messages.messageFor("create-sale.already-registered"));
            }
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            cause.printStackTrace();
            sender.sendMessage(messages.messageFor("create-sale.error",
                    Placeholder.unparsed("error", cause.getMessage())));
            return null;
        });
    }

}

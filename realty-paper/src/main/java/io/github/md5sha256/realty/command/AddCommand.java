package io.github.md5sha256.realty.command;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.RegionOrFlagParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.incendo.cloud.paper.util.sender.Source;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Handles {@code /realty add <player|group> [region] [--group]}. With {@code --group}, the name is a
 * permission group, added to the region's WorldGuard members as a group; no account mapping is
 * needed for that, since a member takes no contract role.
 *
 * <p>Base permission: {@code realty.command.add}.
 * Acting on another player's region additionally requires {@code realty.command.add.others}.</p>
 */
public record AddCommand(@NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("add")
                .permission("realty.command.add")
                .required("player", StringParser.stringParser(), playerSuggestions())
                // The region steps aside for --group, which Cloud reads only after the last argument.
                .optional("region", RegionOrFlagParser.regionOrFlag())
                .flag(CommandFlag.builder("group"))
                .handler(this::execute)
                .build();
    }

    private static @NotNull SuggestionProvider<Source> playerSuggestions() {
        return (ctx, input) -> CompletableFuture.completedFuture(
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .map(Suggestion::suggestion)
                        .toList()
        );
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        String playerOrGroup = ctx.get("player");
        boolean isGroup = ctx.flags().isPresent("group");
        WorldGuardRegion region = ctx.<Optional<WorldGuardRegion>>optional("region")
                .flatMap(Function.identity())
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();

        if (sender instanceof Player player
                && !mayEditMembers(region.region(), WorldGuardPlugin.inst().wrapPlayer(player),
                        sender.hasPermission("realty.command.add.others"))) {
            sender.sendMessage(messages.messageFor(MessageKeys.ADD_NO_PERMISSION));
            return;
        }
        ProtectedRegion protectedRegion = region.region();
        if (isGroup) {
            protectedRegion.getMembers().addGroup(playerOrGroup);
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerOrGroup);
            protectedRegion.getMembers().addPlayer(target.getUniqueId());
        }
        sender.sendMessage(messages.messageFor(MessageKeys.ADD_SUCCESS,
                Placeholder.unparsed("target", playerOrGroup),
                Placeholder.unparsed("region", regionId)));
    }

    /**
     * Whether {@code player} may edit {@code region}'s member list: either they hold the
     * permission for acting on another player's region, or WorldGuard already considers them
     * an owner, whether listed by UUID or through an owner group.
     */
    static boolean mayEditMembers(@NotNull ProtectedRegion region, @NotNull LocalPlayer player,
                                   boolean hasOthersPermission) {
        return hasOthersPermission || region.isOwner(player);
    }

}

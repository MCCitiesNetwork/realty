package io.github.md5sha256.realty.command.util;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AuthorityParser implements ArgumentParser<CommandSourceStack, UUID> {

    public static @NotNull ParserDescriptor<CommandSourceStack, UUID> authority() {
        return ParserDescriptor.of(new AuthorityParser(), UUID.class);
    }

    @Override
    public @NotNull ArgumentParseResult<UUID> parse(
            @NotNull CommandContext<CommandSourceStack> ctx,
            @NotNull CommandInput input
    ) {
        String name = input.readString();
        Player onlinePlayer = Bukkit.getPlayerExact(name);
        if (onlinePlayer != null) {
            return ArgumentParseResult.success(onlinePlayer.getUniqueId());
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(name);
        if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
            return ArgumentParseResult.failure(
                    new IllegalArgumentException("Player not found: " + name));
        }
        return ArgumentParseResult.success(offlinePlayer.getUniqueId());
    }

    @Override
    public @NotNull SuggestionProvider<CommandSourceStack> suggestionProvider() {
        return (ctx, input) -> CompletableFuture.completedFuture(
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .map(Suggestion::suggestion)
                        .toList()
        );
    }
}

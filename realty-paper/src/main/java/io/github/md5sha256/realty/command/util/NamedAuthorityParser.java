package io.github.md5sha256.realty.command.util;

import org.incendo.cloud.paper.util.sender.Source;
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

import java.util.concurrent.CompletableFuture;

public class NamedAuthorityParser implements ArgumentParser<Source, NamedAuthority> {

    public static @NotNull ParserDescriptor<Source, NamedAuthority> namedAuthority() {
        return ParserDescriptor.of(new NamedAuthorityParser(), NamedAuthority.class);
    }

    @Override
    public @NotNull ArgumentParseResult<NamedAuthority> parse(
            @NotNull CommandContext<Source> ctx,
            @NotNull CommandInput input
    ) {
        String name = input.readString();
        Player onlinePlayer = Bukkit.getPlayerExact(name);
        if (onlinePlayer != null) {
            return ArgumentParseResult.success(
                    new NamedAuthority(onlinePlayer.getUniqueId(), onlinePlayer.getName()));
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(name);
        if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
            return ArgumentParseResult.failure(
                    new IllegalArgumentException("Player not found: " + name));
        }
        return ArgumentParseResult.success(
                new NamedAuthority(offlinePlayer.getUniqueId(), offlinePlayer.getName()));
    }

    @Override
    public @NotNull SuggestionProvider<Source> suggestionProvider() {
        return (ctx, input) -> CompletableFuture.completedFuture(
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .map(Suggestion::suggestion)
                        .toList()
        );
    }
}

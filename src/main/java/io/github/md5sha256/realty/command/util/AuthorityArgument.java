package io.github.md5sha256.realty.command.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AuthorityArgument implements CustomArgumentType<UUID, String> {

    private static final DynamicCommandExceptionType ERROR_PLAYER_NOT_FOUND = new DynamicCommandExceptionType(
            playerName -> () -> "Player not found: " + playerName
    );

    @Override
    public UUID parse(@NotNull StringReader reader) throws CommandSyntaxException {
        String name = getNativeType().parse(reader);
        Player onlinePlayer = Bukkit.getPlayerExact(name);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(name);

        if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
            throw ERROR_PLAYER_NOT_FOUND.create(name);
        }

        return offlinePlayer.getUniqueId();
    }

    @Override
    @NotNull
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    @NotNull
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context,
                                                              SuggestionsBuilder builder) {
        Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .forEach(builder::suggest);
        return builder.buildFuture();
    }
}

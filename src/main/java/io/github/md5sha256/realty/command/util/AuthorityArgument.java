package io.github.md5sha256.realty.command.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class AuthorityArgument implements CustomArgumentType<UUID, String> {

    private static final DynamicCommandExceptionType ERROR_PLAYER_NOT_FOUND = new DynamicCommandExceptionType(
            playerName -> () -> "Player not found: " + playerName
    );

    @Override
    public UUID parse(@NotNull StringReader reader) throws CommandSyntaxException {
        String name = getNativeType().parse(reader);
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
}

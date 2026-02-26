package io.github.md5sha256.realty.command.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class WorldGuardRegionArgument implements CustomArgumentType<WorldGuardRegionResolver, String> {

    private static final SimpleCommandExceptionType ERROR_BAD_SOURCE = new SimpleCommandExceptionType(
            () -> "Source must be a CommandSourceStack"
    );

    @Override
    public @NotNull WorldGuardRegionResolver parse(@NotNull StringReader reader) throws CommandSyntaxException {
        throw new UnsupportedOperationException("This method will never be called.");
    }

    @Override
    public <S> @NotNull WorldGuardRegionResolver parse(@NotNull StringReader reader, @NotNull S source) throws CommandSyntaxException {
        if (!(source instanceof CommandSourceStack stack)) {
            throw ERROR_BAD_SOURCE.create();
        }

        String regionName = reader.readUnquotedString();
        return new WorldGuardRegionResolver(regionName, stack);
    }

    @Override
    public <S> @NotNull CompletableFuture<Suggestions> listSuggestions(
            @NotNull CommandContext<S> context,
            @NotNull SuggestionsBuilder builder
    ) {
        S source = context.getSource();
        if (source instanceof CommandSourceStack stack) {
            World world = stack.getLocation().getWorld();
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager != null) {
                String remaining = builder.getRemainingLowerCase();
                for (String id : regionManager.getRegions().keySet()) {
                    if (id.toLowerCase().startsWith(remaining)) {
                        builder.suggest(id);
                    }
                }
            }
        }
        return builder.buildFuture();
    }

    @Override
    public @NotNull ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }
}

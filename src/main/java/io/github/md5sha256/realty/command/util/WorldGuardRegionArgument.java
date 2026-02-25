package io.github.md5sha256.realty.command.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class WorldGuardRegionArgument implements CustomArgumentType<WorldGuardRegion, String> {

    private static final SimpleCommandExceptionType ERROR_BAD_SOURCE = new SimpleCommandExceptionType(
            () -> "Source must be a CommandSourceStack"
    );

    private static final DynamicCommandExceptionType ERROR_NO_REGION_MANAGER = new DynamicCommandExceptionType(
            worldName -> () -> "No region manager found for world: " + worldName
    );

    private static final DynamicCommandExceptionType ERROR_REGION_NOT_FOUND = new DynamicCommandExceptionType(
            regionName -> () -> "Region not found: " + regionName
    );

    @Override
    public @NotNull WorldGuardRegion parse(@NotNull StringReader reader) throws CommandSyntaxException {
        throw new UnsupportedOperationException("This method will never be called.");
    }

    @Override
    public <S> @NotNull WorldGuardRegion parse(@NotNull StringReader reader, @NotNull S source) throws CommandSyntaxException {
        if (!(source instanceof CommandSourceStack stack)) {
            throw ERROR_BAD_SOURCE.create();
        }

        String regionName = reader.readUnquotedString();
        World world = stack.getLocation().getWorld();

        RegionManager regionManager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(world));

        if (regionManager == null) {
            throw ERROR_NO_REGION_MANAGER.create(world.getName());
        }

        ProtectedRegion region = regionManager.getRegion(regionName);
        if (region == null) {
            throw ERROR_REGION_NOT_FOUND.create(regionName);
        }

        return new WorldGuardRegion(region, world.getUID());
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

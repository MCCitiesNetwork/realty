package io.github.md5sha256.realty.command.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.incendo.cloud.paper.util.sender.Source;
import org.bukkit.World;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WorldGuardRegionParser implements ArgumentParser<Source, WorldGuardRegion> {

    public static @NotNull ParserDescriptor<Source, WorldGuardRegion> worldGuardRegion() {
        return ParserDescriptor.of(new WorldGuardRegionParser(), WorldGuardRegion.class);
    }

    @Override
    public @NotNull ArgumentParseResult<WorldGuardRegion> parse(
            @NotNull CommandContext<Source> ctx,
            @NotNull CommandInput input
    ) {
        String regionName = input.readString();
        Source source = ctx.sender();
        World world = source.stack().getLocation().getWorld();
        RegionManager regionManager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(world));

        if (regionManager == null) {
            return ArgumentParseResult.failure(
                    new IllegalArgumentException("No region manager found for world: " + world.getName()));
        }

        ProtectedRegion region = regionManager.getRegion(regionName);
        if (region == null) {
            return ArgumentParseResult.failure(
                    new IllegalArgumentException("Region not found: " + regionName));
        }

        return ArgumentParseResult.success(new WorldGuardRegion(region, world));
    }

    @Override
    public @NotNull SuggestionProvider<Source> suggestionProvider() {
        return (ctx, input) -> {
            Source source = ctx.sender();
            World world = source.stack().getLocation().getWorld();
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager == null) {
                return CompletableFuture.completedFuture(List.of());
            }

            String remaining = input.lastRemainingToken().toLowerCase();
            List<Suggestion> suggestions = regionManager.getRegions().keySet().stream()
                    .filter(id -> id.toLowerCase().startsWith(remaining))
                    .map(Suggestion::suggestion)
                    .toList();

            return CompletableFuture.completedFuture(suggestions);
        };
    }
}

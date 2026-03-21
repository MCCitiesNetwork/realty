package io.github.md5sha256.realty.command.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.World;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Argument parser that resolves a {@link WorldGuardRegion} from either an explicit
 * region name argument or, when no argument is provided, the player's current location.
 *
 * <p>Use this as an <strong>optional</strong> argument so that the location fallback
 * triggers when the player omits the region name.</p>
 */
public class WorldGuardRegionResolver implements ArgumentParser<CommandSourceStack, WorldGuardRegion> {

    public static @NotNull ParserDescriptor<CommandSourceStack, WorldGuardRegion> worldGuardRegionResolver() {
        return ParserDescriptor.of(new WorldGuardRegionResolver(), WorldGuardRegion.class);
    }

    @Override
    public @NotNull ArgumentParseResult<WorldGuardRegion> parse(
            @NotNull CommandContext<CommandSourceStack> ctx,
            @NotNull CommandInput input
    ) {
        String regionName = input.readString();
        CommandSourceStack source = ctx.sender();
        World world = source.getLocation().getWorld();
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

    /**
     * Resolves the highest-priority WorldGuard region at the given location.
     *
     * @param location the location to check
     * @return the resolved region, or {@code null} if no region contains the location
     */
    public static WorldGuardRegion resolveAtLocation(@NotNull Location location) {
        World world = location.getWorld();
        RegionManager regionManager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (regionManager == null) {
            return null;
        }
        BlockVector3 position = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        ApplicableRegionSet applicable = regionManager.getApplicableRegions(position);
        return applicable.getRegions()
                .stream()
                .max(Comparator.comparingInt(ProtectedRegion::getPriority))
                .map(best -> new WorldGuardRegion(best, world))
                .orElse(null);
    }

    @Override
    public @NotNull SuggestionProvider<CommandSourceStack> suggestionProvider() {
        return (ctx, input) -> {
            CommandSourceStack source = ctx.sender();
            World world = source.getLocation().getWorld();
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

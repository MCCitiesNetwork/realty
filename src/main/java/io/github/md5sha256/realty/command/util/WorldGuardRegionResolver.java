package io.github.md5sha256.realty.command.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A deferred resolver for a {@link WorldGuardRegion}. When an argument string was provided,
 * the region is resolved by name; otherwise, the region is inferred from the sender's
 * current location. The world is always the world the sender is standing in.
 */
public final class WorldGuardRegionResolver {

    private static final SimpleCommandExceptionType ERROR_NO_REGION_AT_LOCATION = new SimpleCommandExceptionType(
            () -> "No WorldGuard region found at your location"
    );

    private static final SimpleCommandExceptionType ERROR_MULTIPLE_REGIONS_AT_LOCATION = new SimpleCommandExceptionType(
            () -> "Multiple WorldGuard regions found at your location; please specify a region name"
    );

    private static final DynamicCommandExceptionType ERROR_NO_REGION_MANAGER = new DynamicCommandExceptionType(
            worldName -> () -> "No region manager found for world: " + worldName
    );

    private static final DynamicCommandExceptionType ERROR_REGION_NOT_FOUND = new DynamicCommandExceptionType(
            regionName -> () -> "Region not found: " + regionName
    );

    private final @Nullable String regionName;
    private final @NotNull CommandSourceStack sourceStack;

    /**
     * Creates a resolver that will look up the region by name.
     *
     * @param regionName  the WorldGuard region ID string
     * @param sourceStack the command source stack (used to determine the world)
     */
    WorldGuardRegionResolver(@NotNull String regionName, @NotNull CommandSourceStack sourceStack) {
        this.regionName = regionName;
        this.sourceStack = sourceStack;
    }

    /**
     * Creates a resolver that will infer the region from the sender's location.
     *
     * @param sourceStack the command source stack (used to determine world and location)
     */
    WorldGuardRegionResolver(@NotNull CommandSourceStack sourceStack) {
        this.regionName = null;
        this.sourceStack = sourceStack;
    }

    /**
     * Attempts to retrieve a {@link WorldGuardRegionResolver} from the command context.
     * If the argument was specified, the resolver parsed from the argument is returned.
     * Otherwise, a location-based resolver is created from the command source.
     *
     * @param ctx          the command context
     * @param argumentName the name of the region argument in the command tree
     * @return a resolver, never null
     */
    public static @NotNull WorldGuardRegionResolver resolve(
            @NotNull CommandContext<CommandSourceStack> ctx,
            @NotNull String argumentName
    ) {
        try {
            return ctx.getArgument(argumentName, WorldGuardRegionResolver.class);
        } catch (IllegalArgumentException ex) {
            return new WorldGuardRegionResolver(ctx.getSource());
        }
    }

    /**
     * Resolves the {@link WorldGuardRegion}. If a region name was provided, looks it up by
     * name in the sender's world. Otherwise, determines the region from the sender's location.
     *
     * @return the resolved region
     * @throws CommandSyntaxException if the region cannot be resolved
     */
    public @NotNull WorldGuardRegion resolve() throws CommandSyntaxException {
        World world = this.sourceStack.getLocation().getWorld();
        RegionManager regionManager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(world));

        if (regionManager == null) {
            throw ERROR_NO_REGION_MANAGER.create(world.getName());
        }

        if (this.regionName != null) {
            return resolveByName(this.regionName, regionManager, world);
        }
        return resolveByLocation(regionManager, world);
    }

    private @NotNull WorldGuardRegion resolveByName(
            @NotNull String regionName,
            @NotNull RegionManager regionManager,
            @NotNull World world
    ) throws CommandSyntaxException {
        ProtectedRegion region = regionManager.getRegion(regionName);
        if (region == null) {
            throw ERROR_REGION_NOT_FOUND.create(regionName);
        }
        return new WorldGuardRegion(region, world);
    }

    private @NotNull WorldGuardRegion resolveByLocation(
            @NotNull RegionManager regionManager,
            @NotNull World world
    ) throws CommandSyntaxException {
        Location location = this.sourceStack.getLocation();
        BlockVector3 position = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        List<ProtectedRegion> regions = regionManager.getApplicableRegions(position).getRegions()
                .stream()
                .toList();

        if (regions.isEmpty()) {
            throw ERROR_NO_REGION_AT_LOCATION.create();
        }
        if (regions.size() > 1) {
            throw ERROR_MULTIPLE_REGIONS_AT_LOCATION.create();
        }
        return new WorldGuardRegion(regions.getFirst(), world);
    }
}

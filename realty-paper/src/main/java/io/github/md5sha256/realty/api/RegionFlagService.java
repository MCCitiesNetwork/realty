package io.github.md5sha256.realty.api;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service that manages and applies WorldGuard flag profiles to regions
 * based on their {@link RegionState}. Supports both global flag profiles
 * (applied to all regions) and grouped flag profiles (applied only to
 * specific named regions).
 */
public class RegionFlagService {

    private final Logger logger;
    private final EnumMap<RegionState, Map<String, String>> globalFlagProfiles;
    private final HashMap<String, EnumMap<RegionState, Map<String, String>>> groupedFlagProfiles;

    public RegionFlagService(@NotNull Logger logger) {
        this.logger = logger;
        this.globalFlagProfiles = new EnumMap<>(RegionState.class);
        this.groupedFlagProfiles = new HashMap<>();
    }

    /**
     * Sets the global flag profile for a given region state, replacing any existing profile.
     *
     * @param state the region state
     * @param flags map of WorldGuard flag names to their string values
     */
    public void setGlobalFlagProfile(@NotNull RegionState state, @NotNull Map<String, String> flags) {
        this.globalFlagProfiles.put(state, new LinkedHashMap<>(flags));
    }

    /**
     * Returns an unmodifiable view of the global flag profile for the given state,
     * or an empty map if no profile is configured.
     *
     * @param state the region state
     * @return the configured flags for that state
     */
    public @NotNull Map<String, String> getGlobalFlagProfile(@NotNull RegionState state) {
        Map<String, String> profile = this.globalFlagProfiles.get(state);
        if (profile == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(profile);
    }

    /**
     * Adds a grouped flag profile that applies to a specific set of region names.
     * Each region name is mapped to the same per-state flag maps.
     *
     * @param regionNames the WG region names this profile applies to
     * @param states      per-state flag maps
     */
    public void addGroupedFlagProfile(@NotNull Set<String> regionNames,
                                      @NotNull Map<RegionState, Map<String, String>> states) {
        EnumMap<RegionState, Map<String, String>> copied = new EnumMap<>(RegionState.class);
        for (Map.Entry<RegionState, Map<String, String>> entry : states.entrySet()) {
            copied.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        for (String regionName : regionNames) {
            this.groupedFlagProfiles.merge(regionName, copied, (existing, incoming) -> {
                for (Map.Entry<RegionState, Map<String, String>> entry : incoming.entrySet()) {
                    existing.merge(entry.getKey(), entry.getValue(), (oldFlags, newFlags) -> {
                        Map<String, String> merged = new LinkedHashMap<>(oldFlags);
                        merged.putAll(newFlags);
                        return merged;
                    });
                }
                return existing;
            });
        }
    }

    /**
     * Clears all grouped flag profiles.
     */
    public void clearGroupedFlagProfiles() {
        this.groupedFlagProfiles.clear();
    }

    /**
     * Applies the global and grouped flag profiles for the given state to the
     * specified WorldGuard region. Flags that cannot be resolved or parsed are
     * logged as warnings and skipped.
     *
     * @param region the WorldGuard region to apply flags to
     * @param state  the region state whose flag profiles should be applied
     */
    public void applyFlags(@NotNull WorldGuardRegion region, @NotNull RegionState state) {
        Map<String, String> globalProfile = this.globalFlagProfiles.get(state);
        if (globalProfile != null && !globalProfile.isEmpty()) {
            applyFlagMap(region.region(), globalProfile);
        }
        EnumMap<RegionState, Map<String, String>> grouped = this.groupedFlagProfiles.get(region.region().getId());
        if (grouped != null) {
            Map<String, String> groupedProfile = grouped.get(state);
            if (groupedProfile != null && !groupedProfile.isEmpty()) {
                applyFlagMap(region.region(), groupedProfile);
            }
        }
    }

    /**
     * Clears all flags that are part of the given state's global and grouped
     * profiles from the region.
     *
     * @param region the WorldGuard region to clear flags from
     * @param state  the region state whose flag profiles should be cleared
     */
    public void clearFlags(@NotNull WorldGuardRegion region, @NotNull RegionState state) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        ProtectedRegion protectedRegion = region.region();
        Map<String, String> globalProfile = this.globalFlagProfiles.get(state);
        if (globalProfile != null) {
            clearFlagKeys(registry, protectedRegion, globalProfile.keySet());
        }
        EnumMap<RegionState, Map<String, String>> grouped = this.groupedFlagProfiles.get(protectedRegion.getId());
        if (grouped != null) {
            Map<String, String> groupedProfile = grouped.get(state);
            if (groupedProfile != null) {
                clearFlagKeys(registry, protectedRegion, groupedProfile.keySet());
            }
        }
    }

    private void clearFlagKeys(@NotNull FlagRegistry registry,
                               @NotNull ProtectedRegion protectedRegion,
                               @NotNull Set<String> flagNames) {
        for (String flagName : flagNames) {
            Flag<?> flag = resolveFlag(registry, flagName);
            if (flag == null) {
                continue;
            }
            protectedRegion.setFlag(flag, null);
        }
    }

    private void applyFlagMap(@NotNull ProtectedRegion protectedRegion,
                              @NotNull Map<String, String> flags) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        for (Map.Entry<String, String> entry : flags.entrySet()) {
            String flagName = entry.getKey();
            String flagValue = entry.getValue();

            Flag<?> flag = resolveFlag(registry, flagName);
            if (flag == null) {
                this.logger.warning("Unknown WorldGuard flag: " + flagName);
                continue;
            }

            try {
                setFlag(protectedRegion, flag, flagValue);
            } catch (InvalidFlagFormat ex) {
                this.logger.log(Level.WARNING,
                        "Invalid value '" + flagValue + "' for flag '" + flagName + "'", ex);
            }
        }
    }

    private @Nullable Flag<?> resolveFlag(@NotNull FlagRegistry registry,
                                          @NotNull String flagName) {
        return registry.get(flagName);
    }

    private <T> void setFlag(@NotNull ProtectedRegion region,
                             @NotNull Flag<T> flag,
                             @NotNull String value) throws InvalidFlagFormat {
        FlagContext context = FlagContext.create()
                .setObject("region", region)
                .setInput(value)
                .build();
        T parsed = flag.parseInput(context);
        region.setFlag(flag, parsed);
    }

}

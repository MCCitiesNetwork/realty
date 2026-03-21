package io.github.md5sha256.realty.api;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.RegionGroupFlag;
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
 * Service that manages and applies WorldGuard flag profiles (including region priority)
 * to regions based on their {@link RegionState}. Supports both global profiles
 * (applied to all regions) and grouped profiles (applied only to specific named regions).
 */
public class RegionProfileService {

    private final Logger logger;
    private final EnumMap<RegionState, FlagProfile> globalFlagProfiles;
    private final HashMap<String, EnumMap<RegionState, FlagProfile>> groupedFlagProfiles;

    public RegionProfileService(@NotNull Logger logger) {
        this.logger = logger;
        this.globalFlagProfiles = new EnumMap<>(RegionState.class);
        this.groupedFlagProfiles = new HashMap<>();
    }

    /**
     * Sets the global flag profile for a given region state, replacing any existing profile.
     *
     * @param state    the region state
     * @param priority the region priority to apply, or null to leave priority unchanged
     * @param flags    map of WorldGuard flag names to their string values
     */
    public void setGlobalFlagProfile(@NotNull RegionState state,
                                     @Nullable Integer priority,
                                     @NotNull Map<String, String> flags) {
        this.globalFlagProfiles.put(state, new FlagProfile(priority, new LinkedHashMap<>(flags)));
    }

    /**
     * Returns an unmodifiable view of the flags for the given global state profile,
     * or an empty map if no profile is configured.
     *
     * @param state the region state
     * @return the configured flags for that state
     */
    public @NotNull Map<String, String> getGlobalFlagProfile(@NotNull RegionState state) {
        FlagProfile profile = this.globalFlagProfiles.get(state);
        if (profile == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(profile.flags());
    }

    /**
     * Adds a grouped flag profile that applies to a specific set of region names.
     * Each region name is mapped to the same per-state flag maps.
     *
     * @param regionNames the WG region names this profile applies to
     * @param states      per-state profiles (priority + flags)
     */
    public void addGroupedFlagProfile(@NotNull Set<String> regionNames,
                                      @NotNull Map<RegionState, FlagProfile> states) {
        EnumMap<RegionState, FlagProfile> copied = new EnumMap<>(RegionState.class);
        for (Map.Entry<RegionState, FlagProfile> entry : states.entrySet()) {
            copied.put(entry.getKey(),
                    new FlagProfile(entry.getValue().priority(),
                            new LinkedHashMap<>(entry.getValue().flags())));
        }
        for (String regionName : regionNames) {
            this.groupedFlagProfiles.merge(regionName, copied, (existing, incoming) -> {
                for (Map.Entry<RegionState, FlagProfile> entry : incoming.entrySet()) {
                    existing.merge(entry.getKey(), entry.getValue(), (oldProfile, newProfile) -> {
                        Map<String, String> merged = new LinkedHashMap<>(oldProfile.flags());
                        merged.putAll(newProfile.flags());
                        Integer priority = newProfile.priority() != null
                                ? newProfile.priority() : oldProfile.priority();
                        return new FlagProfile(priority, merged);
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
     * specified WorldGuard region. All existing flags on the region are cleared
     * before the new profile is applied.
     *
     * @param region the WorldGuard region to apply flags to
     * @param state  the region state whose flag profiles should be applied
     */
    public void applyFlags(@NotNull WorldGuardRegion region, @NotNull RegionState state) {
        ProtectedRegion protectedRegion = region.region();
        protectedRegion.setFlags(Map.of());

        Integer priority = null;
        // Apply global ALL profile as base
        FlagProfile globalAll = this.globalFlagProfiles.get(RegionState.ALL);
        if (globalAll != null) {
            priority = applyProfile(protectedRegion, globalAll, priority);
        }
        // Apply global state-specific profile on top
        if (state != RegionState.ALL) {
            FlagProfile globalState = this.globalFlagProfiles.get(state);
            if (globalState != null) {
                priority = applyProfile(protectedRegion, globalState, priority);
            }
        }
        EnumMap<RegionState, FlagProfile> grouped = this.groupedFlagProfiles.get(protectedRegion.getId());
        if (grouped != null) {
            // Apply grouped ALL profile
            FlagProfile groupedAll = grouped.get(RegionState.ALL);
            if (groupedAll != null) {
                priority = applyProfile(protectedRegion, groupedAll, priority);
            }
            // Apply grouped state-specific profile on top
            if (state != RegionState.ALL) {
                FlagProfile groupedState = grouped.get(state);
                if (groupedState != null) {
                    priority = applyProfile(protectedRegion, groupedState, priority);
                }
            }
        }
        if (priority != null) {
            protectedRegion.setPriority(priority);
        }
    }

    private @Nullable Integer applyProfile(@NotNull ProtectedRegion region,
                                           @NotNull FlagProfile profile,
                                           @Nullable Integer currentPriority) {
        if (!profile.flags().isEmpty()) {
            applyFlagMap(region, profile.flags());
        }
        return profile.priority() != null ? profile.priority() : currentPriority;
    }

    /**
     * Clears all flags from the region and sets priority to 0.
     *
     * @param region the WorldGuard region to clear
     */
    public void clearAllFlags(@NotNull WorldGuardRegion region) {
        ProtectedRegion protectedRegion = region.region();
        protectedRegion.setFlags(Map.of());
        protectedRegion.setPriority(0);
    }

    private void applyFlagMap(@NotNull ProtectedRegion protectedRegion,
                              @NotNull Map<String, String> flags) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        for (Map.Entry<String, String> entry : flags.entrySet()) {
            String flagName = entry.getKey();
            String rawValue = entry.getValue();

            Flag<?> flag = resolveFlag(registry, flagName);
            if (flag == null) {
                this.logger.warning("Unknown WorldGuard flag: " + flagName);
                continue;
            }

            ParsedFlagValue parsed = parseGroupSuffix(rawValue);
            try {
                setFlag(protectedRegion, flag, parsed.value());
            } catch (InvalidFlagFormat ex) {
                this.logger.log(Level.WARNING,
                        "Invalid value '" + parsed.value() + "' for flag '" + flagName + "'", ex);
                continue;
            }

            if (parsed.group() != null) {
                RegionGroupFlag groupFlag = flag.getRegionGroupFlag();
                if (groupFlag != null) {
                    protectedRegion.setFlag(groupFlag, parsed.group());
                } else {
                    this.logger.warning("Flag '" + flagName + "' does not support region groups");
                }
            }
        }
    }

    /**
     * Parses a value string that may contain a {@code -g <GROUP>} suffix.
     * For example, {@code "deny -g NON_MEMBERS"} yields value {@code "deny"}
     * and group {@code NON_MEMBERS}.
     */
    private @NotNull ParsedFlagValue parseGroupSuffix(@NotNull String rawValue) {
        int groupIndex = rawValue.indexOf("-g ");
        if (groupIndex == -1) {
            return new ParsedFlagValue(rawValue.trim(), null);
        }
        String value = rawValue.substring(0, groupIndex).trim();
        String groupName = rawValue.substring(groupIndex + 3).trim();
        try {
            RegionGroup group = RegionGroup.valueOf(groupName.toUpperCase());
            return new ParsedFlagValue(value, group);
        } catch (IllegalArgumentException ex) {
            this.logger.warning("Unknown region group: " + groupName);
            return new ParsedFlagValue(value, null);
        }
    }

    private record ParsedFlagValue(@NotNull String value, @Nullable RegionGroup group) {}

    /**
     * A flag profile containing an optional priority and a map of flag key-value pairs.
     */
    public record FlagProfile(@Nullable Integer priority, @NotNull Map<String, String> flags) {}

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

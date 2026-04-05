package io.github.md5sha256.realty.api;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.RegionGroupFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final EnumMap<RegionState, SignProfile> globalSignProfiles;
    private final HashMap<String, EnumMap<RegionState, SignProfile>> groupedSignProfiles;

    public RegionProfileService(@NotNull Logger logger) {
        this.logger = logger;
        this.globalFlagProfiles = new EnumMap<>(RegionState.class);
        this.groupedFlagProfiles = new HashMap<>();
        this.globalSignProfiles = new EnumMap<>(RegionState.class);
        this.groupedSignProfiles = new HashMap<>();
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
     * Sets the global sign profile for a given region state, replacing any existing profile.
     *
     * @param state   the region state
     * @param profile the sign profile
     */
    public void setGlobalSignProfile(@NotNull RegionState state, @NotNull SignProfile profile) {
        this.globalSignProfiles.put(state, profile);
    }

    /**
     * Adds a grouped sign profile that applies to a specific set of region names.
     *
     * @param regionNames the WG region names this profile applies to
     * @param states      per-state sign profiles
     */
    public void addGroupedSignProfile(@NotNull Set<String> regionNames,
                                       @NotNull Map<RegionState, SignProfile> states) {
        EnumMap<RegionState, SignProfile> copied = new EnumMap<>(RegionState.class);
        copied.putAll(states);
        for (String regionName : regionNames) {
            this.groupedSignProfiles.merge(regionName, copied, (existing, incoming) -> {
                existing.putAll(incoming);
                return existing;
            });
        }
    }

    /**
     * Clears all grouped sign profiles.
     */
    public void clearGroupedSignProfiles() {
        this.groupedSignProfiles.clear();
    }

    /**
     * Resolves the effective sign profile for a region and state by layering
     * global ALL -> global state -> grouped ALL -> grouped state (later overrides earlier).
     * Placeholder tokens in lines and commands are substituted.
     *
     * @param regionId     the WG region ID
     * @param state        the region state
     * @param placeholders placeholder key-value pairs for substitution
     * @return the resolved sign profile, or null if no sign profile is configured
     */
    public @Nullable ResolvedSignProfile resolveSignProfile(@NotNull String regionId,
                                                             @NotNull RegionState state,
                                                             @NotNull Map<String, String> placeholders) {
        SignProfile effective = null;

        // Global ALL
        SignProfile globalAll = this.globalSignProfiles.get(RegionState.ALL);
        if (globalAll != null) {
            effective = globalAll;
        }
        // Global state-specific
        if (state != RegionState.ALL) {
            SignProfile globalState = this.globalSignProfiles.get(state);
            if (globalState != null) {
                effective = globalState;
            }
        }
        // Grouped ALL
        EnumMap<RegionState, SignProfile> grouped = this.groupedSignProfiles.get(regionId);
        if (grouped != null) {
            SignProfile groupedAll = grouped.get(RegionState.ALL);
            if (groupedAll != null) {
                effective = groupedAll;
            }
            // Grouped state-specific
            if (state != RegionState.ALL) {
                SignProfile groupedState = grouped.get(state);
                if (groupedState != null) {
                    effective = groupedState;
                }
            }
        }

        if (effective == null) {
            return null;
        }

        MiniMessage miniMessage = MiniMessage.miniMessage();
        List<Component> resolvedLines = new ArrayList<>(effective.lines().size());
        for (String line : effective.lines()) {
            resolvedLines.add(miniMessage.deserialize(replacePlaceholders(line, placeholders)));
        }

        List<String> resolvedRightClick = resolveCommands(effective.rightClickCommands(), placeholders);
        List<String> resolvedLeftClick = resolveCommands(effective.leftClickCommands(), placeholders);

        return new ResolvedSignProfile(resolvedLines, resolvedRightClick, resolvedLeftClick);
    }

    private @NotNull List<String> resolveCommands(@Nullable List<String> commands,
                                                   @NotNull Map<String, String> placeholders) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        List<String> resolved = new ArrayList<>(commands.size());
        for (String command : commands) {
            resolved.add(replacePlaceholders(command, placeholders));
        }
        return resolved;
    }

    /**
     * Applies the global and grouped flag profiles for the given state to the
     * specified WorldGuard region. All existing flags on the region are cleared
     * before the new profile is applied. Placeholder tokens in flag values
     * (e.g. {@code {region}}, {@code {price}}) are replaced with the
     * corresponding values from the provided map before being parsed by WorldGuard.
     *
     * @param region       the WorldGuard region to apply flags to
     * @param state        the region state whose flag profiles should be applied
     * @param placeholders placeholder key-value pairs for substitution in flag values
     */
    public void applyFlags(@NotNull WorldGuardRegion region,
                           @NotNull RegionState state,
                           @NotNull Map<String, String> placeholders) {
        ProtectedRegion protectedRegion = region.region();
        protectedRegion.setFlags(Map.of());

        Integer priority = null;
        // Apply global ALL profile as base
        FlagProfile globalAll = this.globalFlagProfiles.get(RegionState.ALL);
        if (globalAll != null) {
            priority = applyProfile(protectedRegion, globalAll, placeholders, priority);
        }
        // Apply global state-specific profile on top
        if (state != RegionState.ALL) {
            FlagProfile globalState = this.globalFlagProfiles.get(state);
            if (globalState != null) {
                priority = applyProfile(protectedRegion, globalState, placeholders, priority);
            }
        }
        EnumMap<RegionState, FlagProfile> grouped = this.groupedFlagProfiles.get(protectedRegion.getId());
        if (grouped != null) {
            // Apply grouped ALL profile
            FlagProfile groupedAll = grouped.get(RegionState.ALL);
            if (groupedAll != null) {
                priority = applyProfile(protectedRegion, groupedAll, placeholders, priority);
            }
            // Apply grouped state-specific profile on top
            if (state != RegionState.ALL) {
                FlagProfile groupedState = grouped.get(state);
                if (groupedState != null) {
                    priority = applyProfile(protectedRegion, groupedState, placeholders, priority);
                }
            }
        }
        if (priority != null) {
            protectedRegion.setPriority(priority);
        }
    }

    private @Nullable Integer applyProfile(@NotNull ProtectedRegion region,
                                           @NotNull FlagProfile profile,
                                           @NotNull Map<String, String> placeholders,
                                           @Nullable Integer currentPriority) {
        if (!profile.flags().isEmpty()) {
            applyFlagMap(region, profile.flags(), placeholders);
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
                              @NotNull Map<String, String> flags,
                              @NotNull Map<String, String> placeholders) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        for (Map.Entry<String, String> entry : flags.entrySet()) {
            String flagName = entry.getKey();
            String rawValue = replacePlaceholders(entry.getValue(), placeholders);

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

    private @NotNull String replacePlaceholders(@NotNull String value,
                                                @NotNull Map<String, String> placeholders) {
        if (placeholders.isEmpty() || value.indexOf('<') == -1) {
            return value;
        }
        String result = value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
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

    /**
     * A resolved sign profile with placeholder-substituted lines (as Components) and commands.
     */
    public record ResolvedSignProfile(@NotNull List<Component> lines,
                                       @NotNull List<String> rightClickCommands,
                                       @NotNull List<String> leftClickCommands) {}

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

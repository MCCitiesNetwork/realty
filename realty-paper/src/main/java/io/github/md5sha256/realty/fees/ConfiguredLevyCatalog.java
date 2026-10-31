package io.github.md5sha256.realty.fees;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.LevyCatalog;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.LevyRule;
import io.github.md5sha256.realty.api.LevyTier;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.command.util.PartyResolver;
import io.github.md5sha256.realty.settings.DefaultParties;
import io.github.md5sha256.realty.settings.FeeSettings;
import io.github.md5sha256.realty.settings.LevyRuleSetting;
import io.github.md5sha256.realty.settings.LevySetting;
import io.github.md5sha256.realty.settings.PartySetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** The levies of {@code fees.yml}, compiled. */
public final class ConfiguredLevyCatalog implements LevyCatalog {

    private final Map<String, LevyDefinition> levies;
    private final @Nullable LevyDefinition defaultLevy;

    private ConfiguredLevyCatalog(@NotNull Map<String, LevyDefinition> levies, @Nullable LevyDefinition defaultLevy) {
        this.levies = levies;
        this.defaultLevy = defaultLevy;
    }

    @Override
    public @NotNull Optional<LevyDefinition> find(@NotNull String name) {
        return Optional.ofNullable(this.levies.get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public @NotNull Optional<LevyDefinition> defaultLevy() {
        return Optional.ofNullable(this.defaultLevy);
    }

    @Override
    public @NotNull List<LevyDefinition> all() {
        return List.copyOf(this.levies.values());
    }

    /**
     * Compiles {@code settings}. A levy that is unnamed, has no {@code paid-by}, repeats a name or
     * has a recipient that does not resolve is dropped with a SEVERE log. More than one default
     * levy gives {@link LevyCatalog#EMPTY}. Does blocking I/O when a recipient names an account or
     * a player; call it on the database executor.
     */
    public static @NotNull LevyCatalog compile(@NotNull FeeSettings settings, @NotNull PartyResolver resolver,
                                               @NotNull Logger logger) {
        long defaults = settings.levies().stream().filter(LevySetting::isDefault).count();
        if (defaults > 1) {
            logger.severe("fees.yml marks " + defaults + " levies as default, and at most one may be. "
                    + "No levies are loaded.");
            return LevyCatalog.EMPTY;
        }
        Map<String, LevyDefinition> levies = new LinkedHashMap<>();
        LevyDefinition defaultLevy = null;
        for (LevySetting setting : settings.levies()) {
            LevyDefinition levy = compileLevy(setting, resolver, logger);
            if (levy == null) {
                continue;
            }
            String key = levy.name().toLowerCase(Locale.ROOT);
            if (levies.containsKey(key)) {
                logger.severe("fees.yml defines the levy '" + levy.name() + "' more than once; the later one is dropped");
                continue;
            }
            levies.put(key, levy);
            if (levy.isDefault()) {
                defaultLevy = levy;
            }
        }
        return new ConfiguredLevyCatalog(Map.copyOf(levies), defaultLevy);
    }

    private static @Nullable LevyDefinition compileLevy(@NotNull LevySetting setting, @NotNull PartyResolver resolver,
                                                        @NotNull Logger logger) {
        String name = setting.name();
        if (name == null || name.isBlank()) {
            logger.severe("fees.yml has a levy with no name; it is dropped");
            return null;
        }
        FeeSide paidBy = setting.paidBy();
        if (paidBy == null) {
            logger.severe("fees.yml levy '" + name + "' has no paid-by (buyer or seller); it is dropped");
            return null;
        }
        Party recipient = null;
        PartySetting recipientSetting;
        try {
            recipientSetting = recipientSetting(setting.recipient());
        } catch (SerializationException ex) {
            logger.severe("fees.yml levy '" + name + "' has an invalid recipient (" + ex.getMessage()
                    + "); it is dropped");
            return null;
        }
        if (recipientSetting != null) {
            List<String> errors = new ArrayList<>();
            recipient = DefaultParties.resolveSetting("levy '" + name + "' recipient", recipientSetting,
                    resolver, errors);
            if (recipient == null) {
                errors.forEach(logger::severe);
                logger.severe("fees.yml levy '" + name + "' is dropped because its recipient does not resolve");
                return null;
            }
        }
        List<LevyRule> rules = new ArrayList<>();
        for (LevyRuleSetting rule : setting.rules()) {
            List<LevyTier> tiers = rule.tiers().stream()
                    .map(tier -> new LevyTier(tier.fromPlots(), tier.percent()))
                    .toList();
            rules.add(new LevyRule(new java.util.LinkedHashSet<>(rule.match().all()),
                    new java.util.LinkedHashSet<>(rule.match().any()), tiers));
        }
        return new LevyDefinition(name, setting.enabled(), setting.isDefault(), paidBy, recipient,
                setting.authorityKind(), rules);
    }

    /**
     * @return {@code null} for the plot's authority (omitted, or the string {@code authority}),
     * else the party the value names.
     * @throws SerializationException if a map value is not a valid party.
     */
    private static @Nullable PartySetting recipientSetting(@Nullable ConfigurationNode node)
            throws SerializationException {
        if (node == null || node.virtual() || node.empty() && !node.isMap()) {
            return null;
        }
        if (node.isMap()) {
            PartySetting party = node.get(PartySetting.class);
            if (party == null || party.name() == null && party.uuid() == null && party.type() == null) {
                return null;
            }
            if (party.type() == null && party.uuid() == null && party.name() != null
                    && party.name().trim().equalsIgnoreCase("authority")) {
                return null;
            }
            return party;
        }
        String text = node.getString();
        return text == null || text.isBlank() || text.trim().equalsIgnoreCase("authority")
                ? null : new PartySetting(text.trim(), null, null);
    }
}

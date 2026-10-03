package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.command.util.PartyFlag;
import io.github.md5sha256.realty.command.util.PartyResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The default parties of {@code settings.yml}, resolved. A default that could not be resolved is
 * {@code null} and has a line in {@code errors}; the commands that need it refuse to run.
 *
 * @param freeholdTitleholder           a player, or {@code null} for a region with no titleholder
 * @param freeholdTitleholderUnresolved whether a default titleholder is set but could not be
 *                                      resolved. {@code freeholdTitleholder} is then {@code null},
 *                                      and a command must refuse rather than create a freehold
 *                                      that nobody holds.
 */
public record DefaultParties(@Nullable Party freeholdAuthority,
                             @Nullable Party leaseholdLandlord,
                             @Nullable UUID freeholdTitleholder,
                             boolean freeholdTitleholderUnresolved,
                             @NotNull List<String> errors) {

    private static final String AUTHORITY_KEY = "default-freehold-authority";
    private static final String LANDLORD_KEY = "default-leasehold-landlord";
    private static final String TITLEHOLDER_KEY = "default-freehold-titleholder";

    /** No default is resolved yet, so every command that needs one refuses. */
    public static @NotNull DefaultParties unresolved() {
        return new DefaultParties(null, null, null, true, List.of());
    }

    /**
     * Does blocking I/O when a setting names an account or a player by name; call it on the
     * database executor.
     */
    public static @NotNull DefaultParties resolve(@NotNull Settings settings, @NotNull PartyResolver resolver) {
        List<String> errors = new ArrayList<>();
        Party authority = resolveRequired(AUTHORITY_KEY, settings.defaultFreeholdAuthority(), resolver, errors);
        Party landlord = resolveRequired(LANDLORD_KEY, settings.defaultLeaseholdLandlord(), resolver, errors);
        PartySetting titleholderSetting = settings.defaultFreeholdTitleholder();
        UUID titleholder = resolveTitleholder(titleholderSetting, resolver, errors);
        boolean titleholderUnresolved = titleholderSetting != null && titleholder == null;
        return new DefaultParties(authority, landlord, titleholder, titleholderUnresolved, List.copyOf(errors));
    }

    private static @Nullable Party resolveRequired(@NotNull String key, @Nullable PartySetting setting,
                                                   @NotNull PartyResolver resolver, @NotNull List<String> errors) {
        if (setting == null) {
            errors.add(key + " is not set");
            return null;
        }
        return resolveSetting(key, setting, resolver, errors);
    }

    private static @Nullable UUID resolveTitleholder(@Nullable PartySetting setting, @NotNull PartyResolver resolver,
                                                     @NotNull List<String> errors) {
        if (setting == null) {
            return null;
        }
        if (setting.type() != null) {
            errors.add(TITLEHOLDER_KEY + " must be a player, so it cannot have a type");
            return null;
        }
        return Party.playerUuidOf(resolveSetting(TITLEHOLDER_KEY, setting, resolver, errors)).orElse(null);
    }

    private static @Nullable Party resolveSetting(@NotNull String key, @NotNull PartySetting setting,
                                                  @NotNull PartyResolver resolver, @NotNull List<String> errors) {
        PartyFlag type = setting.type();
        if (type == null && setting.uuid() != null) {
            return new Party.Personal(setting.uuid());
        }
        String name = setting.name();
        if (name == null || name.isBlank()) {
            errors.add(key + " needs a name" + (type == null ? " or a uuid" : ""));
            return null;
        }
        if ((type == PartyFlag.BUSINESS || type == PartyFlag.SYSTEM) && !name.startsWith("#")) {
            errors.add(key + " names a " + type.name().toLowerCase() + " account, which must be given as #<id>, not '"
                    + name + "'");
            return null;
        }
        return switch (resolver.resolve(name, type, null)) {
            case PartyResolver.Resolution.Resolved resolved -> resolved.party();
            case PartyResolver.Resolution.Refused refused -> {
                errors.add(key + " could not be resolved: '" + refused.name() + "' (" + refused.messageKey() + ")");
                yield null;
            }
        };
    }
}

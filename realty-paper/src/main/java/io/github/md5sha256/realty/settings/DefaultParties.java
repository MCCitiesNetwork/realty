package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.command.util.PartyFlag;
import io.github.md5sha256.realty.command.util.PartyResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * @param escrow                        the player whose account holds instalments on Vault, or
 *                                      {@code null} when none is set or it could not be resolved
 */
public record DefaultParties(@Nullable Party freeholdAuthority,
                             @Nullable Party leaseholdLandlord,
                             @Nullable UUID freeholdTitleholder,
                             boolean freeholdTitleholderUnresolved,
                             @Nullable UUID escrow,
                             @NotNull List<String> errors) {

    private static final String AUTHORITY_KEY = "default-freehold-authority";
    private static final String LANDLORD_KEY = "default-leasehold-landlord";
    private static final String TITLEHOLDER_KEY = "default-freehold-titleholder";
    private static final String ESCROW_KEY = "escrow";

    /** No default is resolved yet, so every command that needs one refuses. */
    public static @NotNull DefaultParties unresolved() {
        return new DefaultParties(null, null, null, true, null, List.of());
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
        UUID titleholder = resolvePlayer(TITLEHOLDER_KEY, titleholderSetting, resolver, errors);
        boolean titleholderUnresolved = titleholderSetting != null && titleholder == null;
        UUID escrow = resolvePlayer(ESCROW_KEY, settings.escrow(), resolver, errors);
        return new DefaultParties(authority, landlord, titleholder, titleholderUnresolved, escrow,
                List.copyOf(errors));
    }

    private static @Nullable Party resolveRequired(@NotNull String key, @Nullable PartySetting setting,
                                                   @NotNull PartyResolver resolver, @NotNull List<String> errors) {
        if (setting == null) {
            errors.add(key + " is not set");
            return null;
        }
        return resolveSetting(key, setting, resolver, errors).orElse(null);
    }

    /** A setting that names a player only, or nobody. */
    private static @Nullable UUID resolvePlayer(@NotNull String key, @Nullable PartySetting setting,
                                                @NotNull PartyResolver resolver, @NotNull List<String> errors) {
        if (setting == null) {
            return null;
        }
        if (setting.type() != null) {
            errors.add(key + " must be a player, so it cannot have a type");
            return null;
        }
        return resolveSetting(key, setting, resolver, errors)
                .flatMap(Party::playerUuidOf)
                .orElse(null);
    }

    /** Resolves one named party; a failure is added to {@code errors} and gives an empty result. */
    public static @NotNull Optional<Party> resolveSetting(@NotNull String key, @NotNull PartySetting setting,
                                                          @NotNull PartyResolver resolver,
                                                          @NotNull List<String> errors) {
        PartyFlag type = setting.type();
        if (type == null && setting.uuid() != null) {
            return Optional.of(Party.personal(setting.uuid()));
        }
        String name = setting.name();
        if (name == null || name.isBlank()) {
            errors.add(key + " needs a name" + (type == null ? " or a uuid" : ""));
            return Optional.empty();
        }
        if ((type == PartyFlag.BUSINESS || type == PartyFlag.SYSTEM) && !name.startsWith("#")) {
            errors.add(key + " names a " + type.name().toLowerCase() + " account, which must be given as #<id>, not '"
                    + name + "'");
            return Optional.empty();
        }
        return switch (resolver.resolve(name, type, null)) {
            case PartyResolver.Resolution.Resolved resolved -> Optional.of(resolved.party());
            case PartyResolver.Resolution.Refused refused -> {
                errors.add(key + " could not be resolved: '" + refused.name() + "' (" + refused.messageKey() + ")");
                yield Optional.empty();
            }
        };
    }
}

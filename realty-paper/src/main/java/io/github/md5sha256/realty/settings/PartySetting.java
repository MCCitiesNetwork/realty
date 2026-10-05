package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.command.util.PartyFlag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.UUID;

/**
 * A party named in {@code settings.yml}. With no {@code type} it is a player, given by
 * {@code name} or by {@code uuid}; with a {@code type} it is a Treasury account or a group,
 * given by {@code name}.
 */
@ConfigSerializable
public record PartySetting(@Nullable String name, @Nullable UUID uuid, @Nullable PartyFlag type) {

    /**
     * Configurate builds an empty setting for a key that is absent from the file; that is the same
     * as the key not being set.
     */
    static @Nullable PartySetting nullIfEmpty(@Nullable PartySetting setting) {
        return setting != null && setting.name() == null && setting.uuid() == null && setting.type() == null
                ? null : setting;
    }
}

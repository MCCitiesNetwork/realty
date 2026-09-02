package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Internal entity record mapping to the {@code RealtyWorld} DDL table.
 *
 * <p>Maps a world's UUID to its Bukkit name. Written by Realty core; read by the
 * REST API so it can resolve a world name without the game server running.</p>
 *
 * @param worldId   UUID of the world
 * @param worldName The world's Bukkit name, which is its folder name on disk and
 *                  may contain spaces
 */
public record RealtyWorldEntity(
        @NotNull UUID worldId,
        @NotNull String worldName
) {
}

package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.ResourcePackResponse;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Reads the resource pack from {@code server.properties}, via the injected {@link Server}.
 */
public record ServerResourcePackSource(@NotNull Server server) implements ResourcePackSource {

    public ServerResourcePackSource {
        Objects.requireNonNull(server, "server");
    }

    @Override
    public @NotNull ResourcePackResponse current() {
        return new ResourcePackResponse(
                blankToNull(this.server.getResourcePack()),
                blankToNull(this.server.getResourcePackHash()),
                this.server.isResourcePackRequired());
    }

    /**
     * An unset resource pack is an empty string in {@code server.properties}, not a null.
     * Reporting {@code ""} would make every consumer test for both.
     */
    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

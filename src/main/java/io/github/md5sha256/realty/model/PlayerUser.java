package io.github.md5sha256.realty.model;

import io.github.md5sha256.realty.api.User;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public class PlayerUser implements User {

    private final UUID uuid;
    private final String name;

    public PlayerUser(@NotNull UUID uuid, @NotNull String name) {
        this.uuid = Objects.requireNonNull(uuid, "Player uuid cannot be null");
        this.name = Objects.requireNonNull(name, "Player name cannot be null");
    }

    @Override
    public @NotNull UUID uuid() {
        return this.uuid;
    }

    @Override
    public @NotNull String name() {
        return this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PlayerUser that = (PlayerUser) o;
        return Objects.equals(uuid, that.uuid) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, name);
    }

    @Override
    public String toString() {
        return "PlayerTenant{" +
                "uuid=" + uuid +
                ", name='" + name + '\'' +
                '}';
    }
}

package io.github.md5sha256.realty.command.util;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record NamedAuthority(@NotNull UUID uuid, @NotNull String name) {
}

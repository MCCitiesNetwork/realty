package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;

public record ErrorResponse(@NotNull String error, @NotNull String message) {
}

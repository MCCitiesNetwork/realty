package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.NotNull;

public record ErrorResponse(@NotNull String error, @NotNull String message) {
}

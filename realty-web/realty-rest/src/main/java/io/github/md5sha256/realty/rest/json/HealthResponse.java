package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;

public record HealthResponse(@NotNull String status, @NotNull String module) {
}

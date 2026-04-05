package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface User {

    @NotNull UUID uuid();

    @NotNull String name();

}

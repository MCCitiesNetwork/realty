package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface RealtyRegion {

    @NotNull UUID world();

    @NotNull String worldGuardRegionId();
}

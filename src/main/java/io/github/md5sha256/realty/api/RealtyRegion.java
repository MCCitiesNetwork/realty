package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface RealtyRegion {

    int realtyRegionId();

    @NotNull UUID world();

    @NotNull String worldGuardRegionId();
}

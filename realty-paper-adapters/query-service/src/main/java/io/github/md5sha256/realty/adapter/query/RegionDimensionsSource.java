package io.github.md5sha256.realty.adapter.query;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Answers for a region's live geometry; empty when no such world or region exists. */
@FunctionalInterface
public interface RegionDimensionsSource {

    @NotNull CompletableFuture<Optional<RegionDimensions>> dimensions(@NotNull UUID worldId,
                                                                      @NotNull String regionId);
}

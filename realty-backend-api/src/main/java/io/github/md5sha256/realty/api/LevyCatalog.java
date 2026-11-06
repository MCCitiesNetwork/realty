package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/** The levies a server defines. */
public interface LevyCatalog {

    /** A catalog with no levies. */
    LevyCatalog EMPTY = new LevyCatalog() {
        @Override
        public @NotNull Optional<LevyDefinition> find(@NotNull String name) {
            return Optional.empty();
        }

        @Override
        public @NotNull Optional<LevyDefinition> defaultLevy() {
            return Optional.empty();
        }

        @Override
        public @NotNull List<LevyDefinition> all() {
            return List.of();
        }
    };

    @NotNull Optional<LevyDefinition> find(@NotNull String name);

    @NotNull Optional<LevyDefinition> defaultLevy();

    @NotNull List<LevyDefinition> all();
}

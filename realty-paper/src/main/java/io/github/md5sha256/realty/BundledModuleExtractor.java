package io.github.md5sha256.realty;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Writes a module jar shipped inside the plugin jar out to the modules directory, once.
 *
 * <p>An existing file is never replaced: an operator who removed or swapped a bundled module
 * keeps that choice across restarts.</p>
 */
public final class BundledModuleExtractor {

    private BundledModuleExtractor() {
    }

    public static void extract(@NotNull Path target,
                               @NotNull Supplier<@Nullable InputStream> resource) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream stream = resource.get()) {
            if (stream == null) {
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(stream, target);
        }
    }
}

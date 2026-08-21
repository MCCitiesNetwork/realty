package io.github.md5sha256.realty;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class BundledModuleExtractionTest {

    @Test
    void extractsWhenAbsent(@TempDir Path moduleDir) throws IOException {
        Path target = moduleDir.resolve("chat-adapter.jar");

        BundledModuleExtractor.extract(target,
                () -> new ByteArrayInputStream("jar-bytes".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("jar-bytes", Files.readString(target));
    }

    @Test
    void neverOverwritesAnExistingFile(@TempDir Path moduleDir) throws IOException {
        Path target = moduleDir.resolve("chat-adapter.jar");
        Files.writeString(target, "operator-replaced-this");

        BundledModuleExtractor.extract(target,
                () -> new ByteArrayInputStream("jar-bytes".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("operator-replaced-this", Files.readString(target));
    }

    @Test
    void missingResourceIsNotFatal(@TempDir Path moduleDir) {
        Path target = moduleDir.resolve("chat-adapter.jar");

        Assertions.assertDoesNotThrow(() -> BundledModuleExtractor.extract(target, () -> null));
        Assertions.assertFalse(Files.exists(target));
    }
}

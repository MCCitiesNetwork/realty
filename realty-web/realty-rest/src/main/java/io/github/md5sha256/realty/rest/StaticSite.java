package io.github.md5sha256.realty.rest;

import io.javalin.http.staticfiles.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * A built front end for this service to serve alongside the API.
 *
 * <p>Optional by construction: {@code null} means the service is a pure API, which is
 * what the standalone deployment passes, so its behaviour is unchanged. The bundled
 * {@code realty-web-dist} build passes a classpath site instead, and that is the whole
 * of the difference between the two deployments.</p>
 *
 * @param directory      the static root -- a filesystem path for {@link Location#EXTERNAL},
 *                       or a classpath resource path such as {@code /web}
 * @param location       whether {@code directory} is on disk or on the classpath
 * @param configOverride a {@code config.json} on disk to serve in place of whatever the
 *                       front end was packaged with, or {@code null} to serve only what
 *                       is packaged. A front end on the classpath cannot carry a
 *                       deployment's own settings -- the jar is built once and installed
 *                       everywhere -- so the bundled build names a path beside the jar
 *                       here. A front end already on disk needs none: its config.json
 *                       sits beside its index.html, where the operator put it.
 */
public record StaticSite(@NotNull String directory,
                         @NotNull Location location,
                         @Nullable Path configOverride) {

    public StaticSite(@NotNull String directory, @NotNull Location location) {
        this(directory, location, null);
    }
}

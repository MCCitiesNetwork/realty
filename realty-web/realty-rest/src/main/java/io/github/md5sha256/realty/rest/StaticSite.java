package io.github.md5sha256.realty.rest;

import io.javalin.http.staticfiles.Location;
import org.jetbrains.annotations.NotNull;

/**
 * A built front end for this service to serve alongside the API.
 *
 * <p>Optional by construction: {@code null} means the service is a pure API, which is
 * what the standalone deployment passes, so its behaviour is unchanged. The bundled
 * {@code realty-web-dist} build passes a classpath site instead, and that is the whole
 * of the difference between the two deployments.</p>
 *
 * @param directory the static root -- a filesystem path for {@link Location#EXTERNAL},
 *                  or a classpath resource path such as {@code /web}
 * @param location  whether {@code directory} is on disk or on the classpath
 */
public record StaticSite(@NotNull String directory, @NotNull Location location) {
}

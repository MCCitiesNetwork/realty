package io.github.md5sha256.realty.dist;

import io.github.md5sha256.realty.rest.RealtyRestMain;
import io.github.md5sha256.realty.rest.StaticSite;
import io.javalin.http.staticfiles.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point for the bundled distribution: the same service {@code realty-rest} runs,
 * additionally serving the explorer from this jar's own resources.
 *
 * <p>The front end is packaged at {@code /web} on the classpath, so this is a single
 * artifact and a single process. That is the whole point: a Pterodactyl egg supervises
 * one foreground command, so two processes cannot be run under one egg -- but one that
 * happens to serve both concerns can.</p>
 *
 * <p>Everything else -- configuration, the schema version gate, the module client --
 * is {@link RealtyRestMain#run}'s, not reimplemented here.</p>
 */
public final class RealtyWebDistMain {

    private RealtyWebDistMain() {
    }

    public static void main(@NotNull String[] args) {
        // The bundled front end has no config.json to edit, so it is synthesised from
        // the environment and served as a route. See WebConfig.
        RealtyRestMain.run(new StaticSite("/web", Location.CLASSPATH,
                WebConfig.render(System::getenv)));
    }

}

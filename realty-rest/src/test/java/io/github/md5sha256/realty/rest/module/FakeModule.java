package io.github.md5sha256.realty.rest.module;

import io.javalin.Javalin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** A stand-in for the query-service module's HTTP surface. */
final class FakeModule {

    static final String SECRET = "hunter2";
    static final UUID WORLD = UUID.fromString("8f4d0000-0000-0000-0000-000000000001");
    static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7e1");

    final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private final long stallMillis;

    FakeModule(long stallMillis) {
        this.stallMillis = stallMillis;
    }

    @NotNull Javalin app() {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        app.before(ctx -> {
            if (!SECRET.equals(ctx.header("X-Realty-Secret"))) {
                ctx.status(401).json(Map.of("error", "UNAUTHORIZED", "message", "nope"));
                ctx.skipRemainingHandlers();
            }
            if (this.stallMillis > 0) {
                Thread.sleep(this.stallMillis);
            }
        });
        app.get("/health", ctx -> ctx.json(Map.of("status", "ok")));
        app.get("/regions/{worldId}/{regionId}/dimensions", ctx -> {
            if (ctx.pathParam("worldId").equals(WORLD.toString())
                    && ctx.pathParam("regionId").equals("downtown_plot_14")) {
                ctx.result("{\"shape\":\"POLYGONAL\",\"minY\":62,\"maxY\":140,\"points\":["
                        + "{\"x\":104,\"z\":-88},{\"x\":131,\"z\":-88},{\"x\":131,\"z\":-61},{\"x\":104,\"z\":-61}]}")
                        .contentType("application/json");
            } else {
                ctx.status(404).json(Map.of("error", "REGION_NOT_FOUND", "message", "no"));
            }
        });
        app.post("/players/names", ctx -> {
            this.receivedBodies.add(ctx.body());
            StringBuilder players = new StringBuilder();
            for (String id : idsIn(ctx.body())) {
                if (!players.isEmpty()) {
                    players.append(',');
                }
                String name = id.equals(NOTCH.toString()) ? "\"Notch\""
                        : id.equals(BEDROCK.toString()) ? "\".Cool Guy 123\"" : "null";
                players.append("{\"id\":\"").append(id).append("\",\"name\":").append(name).append('}');
            }
            ctx.result("{\"players\":[" + players + "]}").contentType("application/json");
        });
        app.post("/players/uuids", ctx -> {
            this.receivedBodies.add(ctx.body());
            String body = ctx.body();
            String player = body.contains("\"Notch\"")
                    ? "{\"id\":\"" + NOTCH + "\",\"name\":\"Notch\"}"
                    : body.contains("\".Cool Guy 123\"")
                    ? "{\"id\":\"" + BEDROCK + "\",\"name\":\".Cool Guy 123\"}"
                    : "{\"id\":null,\"name\":\"nobody\"}";
            ctx.result("{\"players\":[" + player + "]}").contentType("application/json");
        });
        return app;
    }

    /** Pulls the quoted strings out of {@code {"ids":["…","…"]}} without a JSON library. */
    private static @NotNull List<String> idsIn(@NotNull String body) {
        List<String> ids = new ArrayList<>();
        int from = body.indexOf('[');
        int to = body.lastIndexOf(']');
        if (from < 0 || to < from) {
            return ids;
        }
        for (String part : body.substring(from + 1, to).split(",")) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2) {
                ids.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return ids;
    }
}

package io.github.md5sha256.realty.rest.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Parsing the query-service module's {@code packs} array, which replaced its single
 * {@code url}.
 */
class ResourcePackListParsingTest {

    /** A module answering /resource-pack with exactly the body under test. */
    private static void withModule(String body, Consumer<ResourcePack> assertions) {
        Javalin module = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.routes.before(ctx -> {
                if (!FakeModule.SECRET.equals(ctx.header("X-Realty-Secret"))) {
                    ctx.status(401).json(Map.of("error", "UNAUTHORIZED", "message", "nope"));
                    ctx.skipRemainingHandlers();
                }
            });
            config.routes.get("/resource-pack",
                    ctx -> ctx.contentType("application/json").result(body));
        });
        JavalinTest.test(module, (server, http) -> {
            HttpModuleClient client = new HttpModuleClient(
                    URI.create("http://localhost:" + server.port()), FakeModule.SECRET,
                    Duration.ofSeconds(5), HttpClient.newHttpClient(), new ObjectMapper());
            ModuleResult<ResourcePack> result = client.resourcePack();
            Assertions.assertInstanceOf(ModuleResult.Found.class, result, String.valueOf(result));
            assertions.accept(((ModuleResult.Found<ResourcePack>) result).value());
        });
    }
    @Test
    void readsEveryPackInTheOrderTheModuleReportsThem() {
        withModule("""
                {"packs":[
                  {"url":"https://cdn.example.com/override.zip","attribution":[]},
                  {"url":"https://cdn.example.com/base.zip","attribution":[]}
                ],"hash":null,"required":false}
                """, pack -> {
            Assertions.assertEquals(2, pack.packs().size());
            Assertions.assertEquals("https://cdn.example.com/override.zip", pack.packs().get(0).url());
            Assertions.assertEquals("https://cdn.example.com/base.zip", pack.packs().get(1).url());
        });
    }

    @Test
    void keepsEachPacksOwnCredits() {
        withModule("""
                {"packs":[
                  {"url":"https://cdn.example.com/override.zip",
                   "attribution":[{"text":"Example Pack 32x","url":"https://packs.example.com/"}]},
                  {"url":"https://cdn.example.com/base.zip",
                   "attribution":[{"text":"CC BY 4.0","url":null}]}
                ],"hash":null,"required":false}
                """, pack -> {
            Assertions.assertEquals("Example Pack 32x", pack.packs().get(0).attribution().get(0).text());
            Assertions.assertEquals("https://packs.example.com/", pack.packs().get(0).attribution().get(0).url());
            Assertions.assertEquals("CC BY 4.0", pack.packs().get(1).attribution().get(0).text());
            Assertions.assertNull(pack.packs().get(1).attribution().get(0).url());
        });
    }

    @Test
    void anEmptyListIsNoPackRatherThanAFailure() {
        withModule("{\"packs\":[],\"hash\":null,\"required\":false}",
                pack -> Assertions.assertTrue(pack.packs().isEmpty()));
    }

    @Test
    void aPackEntryWithNoUrlIsSkippedRatherThanFailingTheWholeList() {
        // The module rejects one at startup, so this can only be a malformed answer; the
        // other packs are still usable and a preview with some textures beats none.
        withModule("""
                {"packs":[
                  {"attribution":[]},
                  {"url":"https://cdn.example.com/base.zip","attribution":[]}
                ],"hash":null,"required":false}
                """, pack -> {
            Assertions.assertEquals(1, pack.packs().size());
            Assertions.assertEquals("https://cdn.example.com/base.zip", pack.packs().get(0).url());
        });
    }
}

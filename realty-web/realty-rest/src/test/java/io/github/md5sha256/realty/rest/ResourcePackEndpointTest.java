package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.ModuleResult;
import io.github.md5sha256.realty.rest.module.ResourcePack;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

class ResourcePackEndpointTest {

    private static ModuleClient moduleReturning(ModuleResult<ResourcePack> result) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "resourcePack" -> result;
            case "status" -> ModuleClient.Status.OK;
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (ModuleClient) Proxy.newProxyInstance(ModuleClient.class.getClassLoader(),
                new Class<?>[]{ModuleClient.class}, handler);
    }

    @Test
    void reportsTheConfiguredPack() {
        RealtyRestServer server = TestServers.withModule(moduleReturning(
                new ModuleResult.Found<>(new ResourcePack("https://cdn.example.com/p.zip", "abc", true))));
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/v1/resource-pack");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("https://cdn.example.com/p.zip"), body);
            Assertions.assertTrue(body.contains("\"required\":true"), body);
        });
    }

    @Test
    void aServerWithNoPackConfiguredIsAnAnswerNotAnError() {
        // The default in server.properties is empty, so this is the common case. The
        // renderer draws untextured geometry rather than showing a failure.
        RealtyRestServer server = TestServers.withModule(moduleReturning(
                new ModuleResult.Found<>(new ResourcePack(null, null, false))));
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/v1/resource-pack");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"url\":null"));
        });
    }

    @Test
    void anUnreachableModuleIs502NotAnEmptyPack() {
        // "No pack configured" and "could not ask" are different answers, and a client
        // must not mistake the second for the first.
        RealtyRestServer server = TestServers.withModule(
                moduleReturning(new ModuleResult.Unavailable<>()));
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/v1/resource-pack");
            Assertions.assertEquals(502, response.code());
            Assertions.assertTrue(response.body().string().contains("RESOURCE_PACK_UNAVAILABLE"));
        });
    }

    @Test
    void aDisabledModuleIsAlso502() {
        RealtyRestServer server = TestServers.withHealthyDatabase();
        JavalinTest.test(server.javalin(), (app, client) ->
                Assertions.assertEquals(502, client.get("/v1/resource-pack").code()));
    }
}

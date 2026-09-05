package io.github.md5sha256.realty.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

class OpenApiConformanceTest {

    @Test
    void everyRegisteredRouteIsDocumented() {
        Set<String> documented = OpenApiRoutes.documentedPaths();
        Set<String> undocumented = new TreeSet<>(RealtyRestServer.ROUTES);
        undocumented.removeAll(documented);
        Assertions.assertTrue(undocumented.isEmpty(),
                "routes missing from openapi.yaml: " + undocumented);
    }

    @Test
    void everyDocumentedPathIsRegistered() {
        Set<String> registered = new HashSet<>(RealtyRestServer.ROUTES);
        Set<String> unimplemented = new TreeSet<>(OpenApiRoutes.documentedPaths());
        unimplemented.removeAll(registered);
        Assertions.assertTrue(unimplemented.isEmpty(),
                "documented paths with no route: " + unimplemented);
    }
}

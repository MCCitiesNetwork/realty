package io.github.md5sha256.realty.dist;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class WebConfigTest {

    private static String render(Map<String, String> env) {
        return WebConfig.render(env::get);
    }

    @Test
    void servesNothingWhenNothingIsConfigured() {
        // No file is the same as an absent one to the front end, which then uses its own
        // defaults -- so serving an empty document would say less, not more.
        Assertions.assertNull(render(Map.of()));
        Assertions.assertNull(render(Map.of("REALTY_WEB_RESOURCE_PACK_ATTRIBUTION", "   ")));
    }

    @Test
    void rendersTextAndLink() {
        String json = render(Map.of("REALTY_WEB_RESOURCE_PACK_ATTRIBUTION",
                "Faithful 64x|https://faithfulpack.net/"));
        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.contains("\"text\":\"Faithful 64x\""), json);
        Assertions.assertTrue(json.contains("\"href\":\"https://faithfulpack.net/\""), json);
    }

    @Test
    void aLinkIsOptional() {
        String json = render(Map.of("REALTY_WEB_RESOURCE_PACK_ATTRIBUTION", "Example Server"));
        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.contains("\"text\":\"Example Server\""), json);
        Assertions.assertFalse(json.contains("href"), json);
    }

    @Test
    void severalEntriesAreSeparatedBySemicolons() {
        String json = render(Map.of("REALTY_WEB_RESOURCE_PACK_ATTRIBUTION",
                "Faithful 64x|https://faithfulpack.net/ ; Example Server"));
        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.contains("Faithful 64x"), json);
        Assertions.assertTrue(json.contains("Example Server"), json);
    }

    @Test
    void anEntryWithNoTextIsSkippedRatherThanRenderedBlank() {
        Assertions.assertNull(render(Map.of("REALTY_WEB_RESOURCE_PACK_ATTRIBUTION", " ; |https://x.test/ ; ")));
    }

    @Test
    void noApiBaseUrlIsEmitted() {
        // The bundled build serves the API from the page's own origin, so naming a base
        // URL could only ever be a way to get it wrong.
        String json = render(Map.of("REALTY_WEB_RESOURCE_PACK_ATTRIBUTION", "Example"));
        Assertions.assertNotNull(json);
        Assertions.assertFalse(json.contains("apiBaseUrl"), json);
    }
}

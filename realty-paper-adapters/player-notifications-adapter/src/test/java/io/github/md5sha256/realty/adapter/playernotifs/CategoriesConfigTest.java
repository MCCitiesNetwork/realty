package io.github.md5sha256.realty.adapter.playernotifs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Covers the parse from {@code categories.yml} to a {@link NotificationCategoryMapper}.
 *
 * <p>{@code YamlConfiguration} needs no running server, so this exercises the real parser rather
 * than a stand-in.</p>
 */
class CategoriesConfigTest {

    private static NotificationCategoryMapper parse(String yaml) {
        return CategoriesConfig.readMapper(CategoriesConfig.load(new StringReader(yaml)));
    }

    /**
     * The file the module writes into the operator's data folder on first start must itself be a
     * valid category set — a default that fails validation would break every fresh install.
     */
    @Test
    void theBundledDefaultParsesAndDeclaresEveryCategory() throws IOException {
        try (InputStream stream = CategoriesConfigTest.class.getClassLoader()
                .getResourceAsStream("categories.yml")) {
            Assertions.assertNotNull(stream, "categories.yml is missing from the jar");
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                NotificationCategoryMapper mapper =
                        CategoriesConfig.readMapper(CategoriesConfig.load(reader));

                Assertions.assertEquals(
                        List.of("realty.agent", "realty.auction", "realty.offer", "realty.lease",
                                "realty.general"),
                        mapper.dataTypes());
                Assertions.assertEquals("realty.general", mapper.fallbackDataType());
                Assertions.assertEquals("realty.auction", mapper.dataTypeFor("notification.outbid"));
                Assertions.assertEquals("realty.lease",
                        mapper.dataTypeFor("notification.leasehold-terminated-tenant"));
                Assertions.assertEquals("Realty — You were outbid",
                        mapper.titleFor("notification.outbid"));
                Assertions.assertEquals("Realty auctions", mapper.labelFor("realty.auction"));
                Assertions.assertEquals(1, mapper.priorityFor("notification.outbid"));
            }
        }
    }

    @Test
    void anOperatorAddedCategoryIsParsedWithItsMetadata() {
        NotificationCategoryMapper mapper = parse("""
                fallback-category: realty.general
                categories:
                  realty.general:
                    label: "Realty"
                    description: "Everything else"
                    keys:
                      - notification.region-bought
                  realty.staff:
                    label: "Staff alerts"
                    description: "For staff only"
                    title: "Staff"
                    priority: 9
                    keys:
                      - notification.outbid
                """);

        Assertions.assertEquals(List.of("realty.general", "realty.staff"), mapper.dataTypes());
        Assertions.assertEquals("realty.staff", mapper.dataTypeFor("notification.outbid"));
        Assertions.assertEquals("Staff", mapper.titleFor("notification.outbid"));
        Assertions.assertEquals("For staff only", mapper.descriptionFor("realty.staff"));
        Assertions.assertEquals(9, mapper.priorityFor("notification.outbid"));
    }

    @Test
    void anOmittedTitleAndPriorityTakeTheirDefaults() {
        NotificationCategoryMapper mapper = parse("""
                fallback-category: realty.general
                categories:
                  realty.general:
                    label: "Realty"
                    keys:
                      - notification.region-bought
                """);

        Assertions.assertEquals("Realty", mapper.titleFor("notification.region-bought"));
        Assertions.assertEquals(0, mapper.priorityFor("notification.region-bought"));
        Assertions.assertEquals("", mapper.descriptionFor("realty.general"));
    }

    /**
     * {@code fallback-category} is optional; leaving it out keeps the historical behaviour of
     * routing unmapped keys to {@code realty.general}.
     */
    @Test
    void anOmittedFallbackDefaultsToGeneral() {
        NotificationCategoryMapper mapper = parse("""
                categories:
                  realty.general:
                    label: "Realty"
                """);

        Assertions.assertEquals("realty.general", mapper.fallbackDataType());
    }

    @Test
    void aFileWithNoCategoriesSectionIsRejected() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> parse("expiry-days: 30\n"));

        Assertions.assertTrue(thrown.getMessage().contains("categories"), thrown.getMessage());
    }

    /**
     * The pre-1.4.2 file mapped a message key straight to a category name. Parsing that as the new
     * shape would silently produce categories named after message keys, so it is rejected with a
     * message that names the format change.
     */
    @Test
    void theOldFlatFormatIsRejectedWithAnExplanation() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> parse("""
                        categories:
                          notification.outbid: realty.auction
                          notification.region-bought: realty.general
                        """));

        Assertions.assertTrue(thrown.getMessage().contains("notification.outbid"), thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage().contains("pre-1.4.2"), thrown.getMessage());
    }
}

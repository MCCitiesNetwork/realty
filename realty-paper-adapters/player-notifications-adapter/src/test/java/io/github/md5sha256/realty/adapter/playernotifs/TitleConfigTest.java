package io.github.md5sha256.realty.adapter.playernotifs;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Covers how {@code titles.yml} is read: the override an operator writes there wins, anything they
 * have not got wins from the compiled table, and the message keys survive the trip.
 */
class TitleConfigTest {

    private static TitleConfig load(String yaml) throws IOException {
        try (Reader reader = new StringReader(yaml)) {
            return TitleConfig.load(reader);
        }
    }

    private static String plain(TitleConfig titles, String messageKey) {
        return PlainTextComponentSerializer.plainText().serialize(titles.titleFor(messageKey));
    }

    /**
     * The point of the file. Bukkit splits a dotted path into nested sections at load time, so
     * {@code notification.leasehold-expired} only survives as one key if the path separator is
     * neutralised <em>before</em> the document is loaded — which is the failure this asserts against.
     */
    @Test
    void anOverrideWinsOverTheCompiledTitle() throws IOException {
        TitleConfig titles = load("""
                titles:
                  notification.leasehold-expired: Your lease ran out
                """);

        Assertions.assertEquals("Your lease ran out", plain(titles, "notification.leasehold-expired"));
    }

    /**
     * A key the operator's file does not mention — one a newer Realty added after they last touched
     * the file — still renders, from the enum.
     */
    @Test
    void anAbsentKeyFallsBackToTheCompiledTitle() throws IOException {
        TitleConfig titles = load("""
                titles:
                  notification.leasehold-expired: Your lease ran out
                """);

        Assertions.assertEquals(RealtyCategory.titleFor("notification.outbid"),
                plain(titles, "notification.outbid"));
    }

    /** An empty or absent {@code titles} block is an operator who has overridden nothing, not an error. */
    @Test
    void aFileWithNoTitlesBlockFallsBackThroughout() throws IOException {
        TitleConfig titles = load("# nothing set\n");

        Assertions.assertEquals("Lease expired", plain(titles, "notification.leasehold-expired"));
        Assertions.assertEquals(RealtyCategory.GENERAL.label(),
                plain(titles, "notification.some-future-key"));
    }

    /** Values are MiniMessage, so an operator can colour a row without a second format setting. */
    @Test
    void aValueIsParsedAsMiniMessage() throws IOException {
        TitleConfig titles = load("""
                titles:
                  notification.outbid: "<red>Outbid</red>"
                """);

        Assertions.assertEquals("Outbid", plain(titles, "notification.outbid"));
        Assertions.assertEquals(NamedTextColor.RED,
                titles.titleFor("notification.outbid").color());
    }

    /**
     * A key no category claims can still be titled here: Realty gains keys over time and third-party
     * fire sites use keys of their own, so the file is not restricted to what the enum knows.
     */
    @Test
    void anUnclaimedKeyMayStillBeTitled() throws IOException {
        TitleConfig titles = load("""
                titles:
                  notification.some-future-key: A future thing
                """);

        Assertions.assertEquals("A future thing", plain(titles, "notification.some-future-key"));
    }

    /**
     * A blank value is an operator halfway through an edit, not a request for an empty inbox row —
     * PlayerNotifications lists a row by its title alone, so an empty one is an unreadable row.
     */
    @Test
    void aBlankValueFallsBackRatherThanRenderingAnEmptyRow() throws IOException {
        TitleConfig titles = load("""
                titles:
                  notification.outbid: ""
                """);

        Assertions.assertEquals("Outbid", plain(titles, "notification.outbid"));
    }
}

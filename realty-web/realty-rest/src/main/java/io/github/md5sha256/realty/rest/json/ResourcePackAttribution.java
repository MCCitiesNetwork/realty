package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One credit line for the resource pack.
 *
 * <p>A client renders these as text, never as markup, and re-checks {@code url} before
 * following it: the value is written by whoever runs the game server and ends up in a
 * page, so the module's startup validation is the first check rather than the only one.</p>
 *
 * @param text the credit
 * @param url  where the credit links, or {@code null} for plain text
 */
public record ResourcePackAttribution(@NotNull String text, @Nullable String url) {
}

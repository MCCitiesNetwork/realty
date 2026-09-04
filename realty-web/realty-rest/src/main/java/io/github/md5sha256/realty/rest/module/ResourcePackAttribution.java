package io.github.md5sha256.realty.rest.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One credit line for the resource pack, as reported by the query-service module.
 *
 * @param text the credit, meant to be rendered as text and never as markup
 * @param url  where the credit links, or {@code null} for plain text
 */
public record ResourcePackAttribution(@NotNull String text, @Nullable String url) {
}

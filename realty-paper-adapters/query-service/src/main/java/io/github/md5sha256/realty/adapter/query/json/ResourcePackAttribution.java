package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One credit line for the configured resource pack.
 *
 * <p>Most packs are licensed on condition that they are credited, and the pack is named
 * by a setting on the game server that nobody browsing the web explorer can see. This
 * travels with the URL so that the operator who chooses a pack states its credit in the
 * same file, rather than in a second one on a possibly different host.</p>
 *
 * @param text the credit, shown as text
 * @param url  where the credit links, or {@code null} for plain text
 */
public record ResourcePackAttribution(@NotNull String text, @Nullable String url) {
}

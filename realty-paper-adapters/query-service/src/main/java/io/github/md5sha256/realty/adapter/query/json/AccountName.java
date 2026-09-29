package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.Nullable;

/** One account. The name is null when the account is unknown; it is never omitted. */
public record AccountName(int id, @Nullable String name) {
}

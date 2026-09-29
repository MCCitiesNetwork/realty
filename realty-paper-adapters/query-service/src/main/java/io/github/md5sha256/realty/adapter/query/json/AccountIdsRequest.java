package io.github.md5sha256.realty.adapter.query.json;

import java.util.List;

/** Elements are read as plain values so that one that is not an integer can be named in the error. */
public record AccountIdsRequest(List<Object> ids) {
}

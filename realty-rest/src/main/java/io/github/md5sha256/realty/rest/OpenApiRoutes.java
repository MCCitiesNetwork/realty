package io.github.md5sha256.realty.rest;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the bundled {@code openapi.yaml} -- the spec-first source of truth for
 * this service's HTTP contract -- from the classpath.
 *
 * <p>Deliberately does not depend on a YAML library for a single job: the two
 * things this class needs from the document are the set of documented paths
 * (used by {@code OpenApiConformanceTest} to keep {@code RealtyRestServer.ROUTES}
 * and the document in lockstep) and a JSON mirror of the same document for
 * {@code /v1/openapi.json}. Both are produced by a small hand-rolled parser
 * scoped to the block-YAML subset this document actually uses (mappings, block
 * and flow sequences, folded/literal scalars, quoted and plain scalars) rather
 * than a general YAML implementation.</p>
 */
public final class OpenApiRoutes {

    private static final String RESOURCE_PATH = "/openapi.yaml";

    /**
     * Matches a top-level path key under {@code paths:} -- exactly two leading
     * spaces, a leading slash, then a trailing colon with nothing else on the
     * line. Anything nested deeper (path parameters, schema properties under
     * {@code components:}) is indented four or more spaces and will not match.
     */
    private static final Pattern PATH_KEY = Pattern.compile("^ {2}(/\\S+):$");

    private static final String RAW_DOCUMENT = readRawDocument();

    private OpenApiRoutes() {
    }

    private static @NotNull String readRawDocument() {
        try (InputStream in = OpenApiRoutes.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled resource " + RESOURCE_PATH);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read bundled " + RESOURCE_PATH, ex);
        }
    }

    /**
     * @return the bundled document's raw YAML text, verbatim.
     */
    public static @NotNull String rawDocument() {
        return RAW_DOCUMENT;
    }

    /**
     * @return every path documented under {@code paths:}, e.g. {@code /v1/health}.
     * Never silently empty: an empty scan almost certainly means the document
     * moved or the regex stopped matching, so this throws rather than letting
     * the conformance test pass vacuously in one direction.
     */
    public static @NotNull Set<String> documentedPaths() {
        Set<String> paths = new TreeSet<>();
        for (String line : RAW_DOCUMENT.split("\r?\n")) {
            Matcher matcher = PATH_KEY.matcher(line);
            if (matcher.matches()) {
                paths.add(matcher.group(1));
            }
        }
        if (paths.isEmpty()) {
            throw new IllegalStateException(
                    "Parsed zero paths from " + RESOURCE_PATH + " -- the document or the scan regressed");
        }
        return paths;
    }

    /**
     * @return the document parsed into a plain {@code Map}/{@code List}/scalar
     * tree suitable for JSON serialisation via Jackson, for {@code /v1/openapi.json}.
     */
    public static @NotNull Object asParsedTree() {
        List<Line> lines = new ArrayList<>();
        for (String raw : RAW_DOCUMENT.split("\r?\n")) {
            String stripped = stripComment(raw);
            if (stripped.isBlank()) {
                continue;
            }
            int indent = leadingSpaces(stripped);
            lines.add(new Line(indent, stripped.substring(indent)));
        }
        int[] pos = {0};
        return lines.isEmpty() ? Map.of() : parseBlock(lines, pos, lines.get(0).indent);
    }

    private static String stripComment(String raw) {
        // '#' only starts a comment outside a quoted scalar; none of this
        // document's values contain '#', so a plain scan is safe here.
        int hash = raw.indexOf('#');
        return hash < 0 ? raw : raw.substring(0, hash);
    }

    private static int leadingSpaces(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private record Line(int indent, String text) {
    }

    private static @NotNull Object parseBlock(@NotNull List<Line> lines, int @NotNull [] pos, int indent) {
        Map<String, Object> map = null;
        List<Object> list = null;
        while (pos[0] < lines.size() && lines.get(pos[0]).indent >= indent) {
            Line line = lines.get(pos[0]);
            if (line.indent > indent) {
                // Should not happen when called with the current block's own
                // indentation; treat as a malformed nesting and stop.
                break;
            }
            if (line.text.startsWith("- ") || line.text.equals("-")) {
                if (list == null) {
                    list = new ArrayList<>();
                }
                String rest = line.text.equals("-") ? "" : line.text.substring(2);
                int itemIndent = line.indent + 2;
                pos[0]++;
                if (rest.isBlank()) {
                    list.add(pos[0] < lines.size() && lines.get(pos[0]).indent >= itemIndent
                            ? parseBlock(lines, pos, lines.get(pos[0]).indent)
                            : null);
                } else {
                    // Synthesize the item's first "key: value" line at the item's
                    // column so a multi-key mapping list item parses like any
                    // other mapping block.
                    List<Line> itemLines = new ArrayList<>();
                    itemLines.add(new Line(itemIndent, rest));
                    while (pos[0] < lines.size() && lines.get(pos[0]).indent >= itemIndent) {
                        itemLines.add(lines.get(pos[0]));
                        pos[0]++;
                    }
                    int[] itemPos = {0};
                    list.add(looksLikeMappingEntry(rest)
                            ? parseBlock(itemLines, itemPos, itemIndent)
                            : parseScalar(rest));
                }
            } else {
                if (map == null) {
                    map = new LinkedHashMap<>();
                }
                pos[0]++;
                int colon = findKeyColon(line.text);
                String key = unquote(line.text.substring(0, colon).trim());
                String valuePart = line.text.substring(colon + 1).trim();
                Object value;
                if (valuePart.equals(">-") || valuePart.equals(">") || valuePart.equals("|-") || valuePart.equals("|")) {
                    boolean folded = valuePart.startsWith(">");
                    StringBuilder text = new StringBuilder();
                    int childIndent = pos[0] < lines.size() ? lines.get(pos[0]).indent : indent + 2;
                    while (pos[0] < lines.size() && lines.get(pos[0]).indent >= childIndent) {
                        if (!text.isEmpty()) {
                            text.append(folded ? ' ' : '\n');
                        }
                        text.append(lines.get(pos[0]).text);
                        pos[0]++;
                    }
                    value = text.toString();
                } else if (valuePart.isEmpty()) {
                    value = pos[0] < lines.size() && lines.get(pos[0]).indent > indent
                            ? parseBlock(lines, pos, lines.get(pos[0]).indent)
                            : null;
                } else {
                    value = parseScalar(valuePart);
                }
                map.put(key, value);
            }
        }
        if (list != null) {
            return list;
        }
        return map == null ? Map.of() : map;
    }

    private static boolean looksLikeMappingEntry(@NotNull String text) {
        return findKeyColon(text) >= 0;
    }

    /**
     * Finds the colon that separates a mapping key from its value on a single
     * line, ignoring a colon that appears inside a quoted scalar (this
     * document's only such case is {@code operationId: "..."}-free plain
     * values, but URLs like {@code "url: /"} still need the first
     * unquoted {@code ": "} or a trailing {@code ":"}).
     */
    private static int findKeyColon(@NotNull String text) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == ':' && !inSingle && !inDouble) {
                if (i + 1 == text.length() || text.charAt(i + 1) == ' ') {
                    return i;
                }
            }
        }
        return -1;
    }

    private static @NotNull Object parseScalar(@NotNull String raw) {
        String value = raw.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            String inner = value.substring(1, value.length() - 1).trim();
            List<Object> flowList = new ArrayList<>();
            if (!inner.isEmpty()) {
                for (String part : inner.split(",")) {
                    flowList.add(parseScalar(part.trim()));
                }
            }
            return flowList;
        }
        String unquoted = unquote(value);
        if (!unquoted.equals(value)) {
            return unquoted;
        }
        if (value.equals("null") || value.equals("~") || value.isEmpty()) {
            return null;
        }
        if (value.equals("true") || value.equals("false")) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            // Not an integer.
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            // Not a number either -- a plain string.
        }
        return value;
    }

    private static @NotNull String unquote(@NotNull String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

}

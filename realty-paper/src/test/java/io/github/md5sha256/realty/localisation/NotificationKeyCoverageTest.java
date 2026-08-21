package io.github.md5sha256.realty.localisation;

import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

class NotificationKeyCoverageTest {

    private static final String EVENT_PACKAGE = "io.github.md5sha256.realty.api.event.";

    /** Keys whose event class name cannot be derived from the key by convention. */
    private static final Map<String, String> EXCEPTIONS = Map.of();

    @Test
    void everyNotificationKeyHasAnEventClass() throws IllegalAccessException {
        List<String> discovered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Field field : MessageKeys.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !field.getName().startsWith("NOTIFICATION_")) {
                continue;
            }
            String key = (String) field.get(null);
            discovered.add(key);
            String className = EXCEPTIONS.getOrDefault(key, deriveClassName(key));
            try {
                Class.forName(EVENT_PACKAGE + className);
            } catch (ClassNotFoundException ex) {
                missing.add(key + " -> " + className);
            }
        }
        Assertions.assertFalse(discovered.isEmpty(),
                "No NOTIFICATION_* constants found in MessageKeys — the reflection filter is broken");
        Assertions.assertEquals(List.of(), missing,
                "Every notification.* key needs a matching event class");
    }

    @Test
    void everyEventClassHasANotificationKey() throws IllegalAccessException, IOException {
        Set<String> keys = new HashSet<>();
        for (Field field : MessageKeys.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && field.getName().startsWith("NOTIFICATION_")) {
                keys.add((String) field.get(null));
            }
        }
        Set<String> classNamesFromKeys = new HashSet<>();
        for (String key : keys) {
            classNamesFromKeys.add(EXCEPTIONS.getOrDefault(key, deriveClassName(key)));
        }

        List<String> discovered = discoverEventClassNames();
        Assertions.assertFalse(discovered.isEmpty(),
                "No event classes discovered — the classpath scan is broken");

        List<String> orphaned = new ArrayList<>();
        for (String className : discovered) {
            if (!classNamesFromKeys.contains(className)) {
                orphaned.add(className);
            }
        }
        Assertions.assertEquals(List.of(), orphaned,
                "Every event class needs a matching notification.* key in MessageKeys");
    }

    /** Concrete {@link RealtyNotificationEvent} subclasses on the classpath, by simple name. */
    private static List<String> discoverEventClassNames() throws IOException {
        String packagePath = EVENT_PACKAGE.substring(0, EVENT_PACKAGE.length() - 1).replace('.', '/');
        List<String> candidates = new ArrayList<>();
        CodeSource codeSource = RealtyNotificationEvent.class.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation() != null) {
            collectFrom(codeSource.getLocation(), packagePath, candidates);
        }
        if (candidates.isEmpty()) {
            Enumeration<URL> resources =
                    NotificationKeyCoverageTest.class.getClassLoader().getResources(packagePath);
            while (resources.hasMoreElements()) {
                collectFrom(resources.nextElement(), packagePath, candidates);
            }
        }
        List<String> concrete = new ArrayList<>();
        for (String simpleName : candidates) {
            try {
                Class<?> candidate = Class.forName(EVENT_PACKAGE + simpleName);
                if (RealtyNotificationEvent.class.isAssignableFrom(candidate)
                        && !Modifier.isAbstract(candidate.getModifiers())) {
                    concrete.add(simpleName);
                }
            } catch (ClassNotFoundException ignored) {
                // Not a class we can see; nothing to assert about it.
            }
        }
        return concrete;
    }

    private static void collectFrom(URL location, String packagePath, List<String> into)
            throws IOException {
        if (!"file".equals(location.getProtocol())) {
            return;
        }
        Path path;
        try {
            path = Path.of(location.toURI());
        } catch (URISyntaxException ex) {
            throw new IOException("Unreadable classpath entry " + location, ex);
        }
        if (Files.isDirectory(path)) {
            Path directory = path.endsWith(packagePath) ? path : path.resolve(packagePath);
            if (!Files.isDirectory(directory)) {
                return;
            }
            try (Stream<Path> entries = Files.list(directory)) {
                entries.map(entry -> entry.getFileName().toString())
                        .filter(name -> name.endsWith(".class") && !name.contains("$"))
                        .map(name -> name.substring(0, name.length() - ".class".length()))
                        .forEach(into::add);
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }
            return;
        }
        if (path.getFileName().toString().endsWith(".jar")) {
            try (JarFile jar = new JarFile(path.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (!name.startsWith(packagePath + "/") || !name.endsWith(".class")
                            || name.contains("$")) {
                        continue;
                    }
                    String simpleName = name.substring(packagePath.length() + 1,
                            name.length() - ".class".length());
                    if (!simpleName.contains("/")) {
                        into.add(simpleName);
                    }
                }
            }
        }
    }

    private static String deriveClassName(String key) {
        String tail = key.substring(key.indexOf('.') + 1);
        StringBuilder builder = new StringBuilder();
        for (String part : tail.split("-")) {
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1));
        }
        return builder.append("Event").toString();
    }
}

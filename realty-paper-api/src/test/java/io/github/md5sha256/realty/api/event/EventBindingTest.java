package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Round-trips every event in this package: each constructor is invoked with a sentinel value that
 * is unique to its parameter position, then every accessor is checked to return the sentinel
 * belonging to the parameter it is named after.
 *
 * <p>Every domain argument on these classes is a bare {@code UUID}, {@code String} or
 * {@code double}, so a transposed assignment — {@code this.inviterId = inviteeId} — compiles
 * cleanly and ships a wrong-but-plausible payload. Because the sentinels differ per position, such
 * a swap makes both accessors return the other's value and the assertion fails. A newly added event
 * class is covered automatically.</p>
 */
class EventBindingTest {

    private static final String PACKAGE_PATH = "io/github/md5sha256/realty/api/event";

    @Test
    void everyEventBindsItsConstructorArgumentsToTheMatchingAccessor() throws Exception {
        List<Class<?>> eventClasses = discoverEventClasses();
        Assertions.assertFalse(eventClasses.isEmpty(),
                "No event classes discovered — the classpath scan is broken");

        List<String> failures = new ArrayList<>();
        for (Class<?> eventClass : eventClasses) {
            for (Constructor<?> constructor : eventClass.getDeclaredConstructors()) {
                if (!Modifier.isPublic(constructor.getModifiers())) {
                    continue;
                }
                checkConstructor(eventClass, constructor, failures);
            }
        }
        Assertions.assertEquals(List.of(), failures,
                "Constructor arguments are not reaching the accessor named after them");
    }

    private static void checkConstructor(Class<?> eventClass,
                                         Constructor<?> constructor,
                                         List<String> failures)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Parameter[] parameters = constructor.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Assertions.assertTrue(parameters[i].isNamePresent(),
                    "Constructor parameter names are missing from " + eventClass.getSimpleName()
                            + " — the -parameters compiler flag is not applied");
            arguments[i] = sentinelFor(parameters[i].getType(), i);
        }
        Object event = constructor.newInstance(arguments);

        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].getName();
            // The single-target convenience constructor wraps its UUID in the targetIds list.
            String accessorName = "targetId".equals(name) ? "targetIds" : name;
            Object expected = "targetId".equals(name) ? List.of(arguments[i]) : arguments[i];
            Method accessor;
            try {
                accessor = eventClass.getMethod(accessorName);
            } catch (NoSuchMethodException ex) {
                failures.add(eventClass.getSimpleName() + " has no accessor '" + accessorName
                        + "()' for constructor parameter " + i + " (" + name + ")");
                continue;
            }
            Object actual = accessor.invoke(event);
            if (!expected.equals(actual)) {
                failures.add(eventClass.getSimpleName() + "." + accessorName + "() returned "
                        + actual + " but parameter " + i + " (" + name + ") was given " + expected
                        + " — arguments look transposed");
            }
        }
    }

    private static Object sentinelFor(Class<?> type, int position) {
        if (type == UUID.class) {
            return new UUID(0xFEEDL, position);
        }
        if (type == List.class) {
            return List.of(new UUID(0xFEEDL, position));
        }
        if (type == String.class) {
            return "sentinel-" + position;
        }
        if (type == Component.class) {
            return Component.text("sentinel-" + position);
        }
        if (type == double.class) {
            return 1000.0d + position;
        }
        if (type == long.class) {
            return 1000L + position;
        }
        if (type == int.class) {
            return 1000 + position;
        }
        if (type == boolean.class) {
            return position % 2 == 0;
        }
        throw new IllegalArgumentException(
                "No sentinel defined for parameter type " + type.getName()
                        + " — extend EventBindingTest.sentinelFor");
    }

    private static List<Class<?>> discoverEventClasses() throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        for (String simpleName : discoverClassNames()) {
            Class<?> candidate = Class.forName(
                    "io.github.md5sha256.realty.api.event." + simpleName);
            if (RealtyNotificationEvent.class.isAssignableFrom(candidate)
                    && !Modifier.isAbstract(candidate.getModifiers())) {
                classes.add(candidate);
            }
        }
        return classes;
    }

    private static List<String> discoverClassNames() throws IOException {
        List<String> names = new ArrayList<>();
        Enumeration<URL> resources =
                EventBindingTest.class.getClassLoader().getResources(PACKAGE_PATH);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if (!"file".equals(url.getProtocol())) {
                continue;
            }
            Path directory;
            try {
                directory = Path.of(url.toURI());
            } catch (URISyntaxException ex) {
                throw new IOException("Unreadable classpath entry " + url, ex);
            }
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> entries = Files.list(directory)) {
                entries.map(path -> path.getFileName().toString())
                        .filter(fileName -> fileName.endsWith(".class"))
                        .filter(fileName -> !fileName.contains("$"))
                        .map(fileName -> fileName.substring(0, fileName.length() - ".class".length()))
                        .forEach(names::add);
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }
        }
        Collections.sort(names);
        return names;
    }
}

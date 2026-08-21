package io.github.md5sha256.realty.localisation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class NotificationKeyCoverageTest {

    private static final String EVENT_PACKAGE = "io.github.md5sha256.realty.api.event.";

    /** Keys whose event class name cannot be derived from the key by convention. */
    private static final Map<String, String> EXCEPTIONS = Map.of();

    @Test
    void everyNotificationKeyHasAnEventClass() throws IllegalAccessException {
        List<String> missing = new ArrayList<>();
        for (Field field : MessageKeys.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !field.getName().startsWith("NOTIFICATION_")) {
                continue;
            }
            String key = (String) field.get(null);
            String className = EXCEPTIONS.getOrDefault(key, deriveClassName(key));
            try {
                Class.forName(EVENT_PACKAGE + className);
            } catch (ClassNotFoundException ex) {
                missing.add(key + " -> " + className);
            }
        }
        Assertions.assertEquals(List.of(), missing,
                "Every notification.* key needs a matching event class");
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

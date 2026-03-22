package io.github.md5sha256.realty.util;

import io.github.md5sha256.realty.settings.Settings;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public final class DateFormatter {

    private DateFormatter() {}

    public static @NotNull String format(@NotNull Settings settings, @NotNull LocalDateTime dateTime) {
        DateFormat dateFormat = settings.dateFormat();
        Date date = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        return dateFormat.format(date);
    }
}

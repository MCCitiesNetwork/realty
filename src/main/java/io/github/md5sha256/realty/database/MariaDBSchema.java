package io.github.md5sha256.realty.database;

import org.jetbrains.annotations.NotNull;


import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MariaDBSchema {

    public void initializeSchema(@NotNull Connection connection, @NotNull InputStream ddlResource) throws IOException, SQLException {
        String ddl = new String(ddlResource.readAllBytes(), StandardCharsets.UTF_8);
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

}

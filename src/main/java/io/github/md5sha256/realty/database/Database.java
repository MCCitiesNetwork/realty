package io.github.md5sha256.realty.database;

import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public interface Database {

    @NotNull SqlSessionWrapper openSession();

    @NotNull SqlSessionWrapper openSession(boolean autoCommit);

    default void initializeSchema(@NotNull InputStream ddlResource) throws IOException, SQLException {
        String ddl = new String(ddlResource.readAllBytes(), StandardCharsets.UTF_8);
        try (SqlSession session = openSession().session();
             Connection connection = session.getConnection();
             Statement statement = connection.createStatement();) {
            statement.execute(ddl);
        }
    }

}

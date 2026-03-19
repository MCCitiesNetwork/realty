package io.github.md5sha256.realty.database;

import org.apache.ibatis.session.ExecutorType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

public interface Database {

    @NotNull SqlSessionWrapper openSession();

    @NotNull SqlSessionWrapper openSession(boolean autoCommit);

    @NotNull SqlSessionWrapper openSession(@NotNull ExecutorType executorType, boolean autoCommit);

    void initializeSchema(@NotNull Path schemaFilesDirectory) throws IOException, SQLException;

}

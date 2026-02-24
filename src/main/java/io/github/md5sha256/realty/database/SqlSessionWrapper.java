package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.mapper.*;
import io.github.md5sha256.realty.database.mapper.maria.*;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;


import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public interface SqlSessionWrapper {

    default void initializeSchema(@NotNull InputStream ddlResource) throws IOException, SQLException {
        String ddl = new String(ddlResource.readAllBytes(), StandardCharsets.UTF_8);
        try (SqlSession session = session();
             Connection connection = session.getConnection();
             Statement statement = connection.createStatement();) {
            statement.execute(ddl);
        }
    }

    @NotNull SqlSession session();

    @NotNull ContractMapper contractMapper();

    @NotNull LeaseContractMapper leaseContractMapper();

    @NotNull RealtyRegionMapper realtyRegionMapper();

    @NotNull SaleContractAuctionMapper saleContractAuctionMapper();

    @NotNull SaleContractMapper saleContractMapper();


}

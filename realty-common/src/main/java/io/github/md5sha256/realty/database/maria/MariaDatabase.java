package io.github.md5sha256.realty.database.maria;

import io.github.md5sha256.realty.DatabaseSettings;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaLeaseContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaRealtyRegionMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractAuctionMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractBidMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractOfferMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractBidPaymentMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractOfferPaymentMapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

public class MariaDatabase implements Database {

    private final DatabaseSettings settings;
    private final SqlSessionFactory sessionFactory;
    private final Logger logger;

    public MariaDatabase(@NotNull DatabaseSettings settings, @NotNull Logger logger) {
        this.settings = settings;
        DataSource dataSource = new PooledDataSource("org.mariadb.jdbc.Driver", "jdbc:" + settings.url(), settings.username(), settings.password());
        this.sessionFactory = buildSessionFactory(dataSource);
        this.logger = logger;
    }

    @NotNull
    private static SqlSessionFactory buildSessionFactory(@NotNull DataSource dataSource) {
        Environment environment = new Environment("production", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UUIDAsBin16Handler.class);
        configuration.addMapper(MariaContractMapper.class);
        configuration.addMapper(MariaLeaseContractMapper.class);
        configuration.addMapper(MariaRealtyRegionMapper.class);
        configuration.addMapper(MariaSaleContractAuctionMapper.class);
        configuration.addMapper(MariaSaleContractBidMapper.class);
        configuration.addMapper(MariaSaleContractMapper.class);
        configuration.addMapper(MariaSaleContractOfferMapper.class);
        configuration.addMapper(MariaSaleContractBidPaymentMapper.class);
        configuration.addMapper(MariaSaleContractOfferPaymentMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    @Override
    public void initializeSchema(@NotNull Path schemaFilesDirectory) throws IOException, SQLException {
        MariaSchemaMigrator.migrate("jdbc:" + this.settings.url(), this.settings.username(), this.settings.password(),
                schemaFilesDirectory, MariaSchemaMigrator.defaultMigrations(), this.logger);
    }

    @Override
    public @NotNull SqlSessionWrapper openSession() {
        return new MariaSqlSession(this.sessionFactory.openSession());
    }

    @Override
    public @NotNull SqlSessionWrapper openSession(boolean autoCommit) {
        return new MariaSqlSession(this.sessionFactory.openSession(autoCommit));
    }

    @Override
    public @NotNull SqlSessionWrapper openSession(@NotNull ExecutorType executorType, boolean autoCommit) {
        return new MariaSqlSession(this.sessionFactory.openSession(executorType, autoCommit));
    }
}

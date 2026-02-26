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
import java.util.UUID;

public class MariaDatabase implements Database {

    private final SqlSessionFactory sessionFactory;

    public MariaDatabase(@NotNull DatabaseSettings settings) {
        this.sessionFactory = buildSessionFactory(settings);
    }

    @NotNull
    private static SqlSessionFactory buildSessionFactory(@NotNull DatabaseSettings settings) {
        DataSource dataSource = new PooledDataSource("org.mariadb.jdbc.Driver", "jdbc:" + settings.url(), settings.username(), settings.password());
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
        return new SqlSessionFactoryBuilder().build(configuration);
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

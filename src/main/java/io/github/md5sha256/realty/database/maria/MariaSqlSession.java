package io.github.md5sha256.realty.database.maria;

import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.ContractMapper;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractBidMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaLeaseContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaRealtyRegionMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractBidMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractMapper;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;

public record MariaSqlSession(@NotNull SqlSession session) implements SqlSessionWrapper {

    @Override
    public @NotNull ContractMapper contractMapper() {
        return session.getMapper(MariaContractMapper.class);
    }

    @Override
    public @NotNull LeaseContractMapper leaseContractMapper() {
        return session.getMapper(MariaLeaseContractMapper.class);
    }

    @Override
    public @NotNull RealtyRegionMapper realtyRegionMapper() {
        return session.getMapper(MariaRealtyRegionMapper.class);
    }

    @Override
    public @NotNull SaleContractBidMapper saleContractAuctionMapper() {
        return session.getMapper(MariaSaleContractBidMapper.class);
    }

    @Override
    public @NotNull SaleContractMapper saleContractMapper() {
        return session.getMapper(MariaSaleContractMapper.class);
    }
}

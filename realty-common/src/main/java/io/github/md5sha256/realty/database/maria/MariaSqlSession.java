package io.github.md5sha256.realty.database.maria;

import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.ContractMapper;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractAuctionMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractBidMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractOfferMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractBidPaymentMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractOfferPaymentMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractSanctionedAuctioneerMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaLeaseContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaRealtyRegionMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractAuctionMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractBidMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractOfferMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractBidPaymentMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractOfferPaymentMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaSaleContractSanctionedAuctioneerMapper;
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
    public @NotNull SaleContractAuctionMapper saleContractAuctionMapper() {
        return session.getMapper(MariaSaleContractAuctionMapper.class);
    }

    @Override
    public @NotNull SaleContractBidMapper saleContractBidMapper() {
        return session.getMapper(MariaSaleContractBidMapper.class);
    }

    @Override
    public @NotNull SaleContractBidPaymentMapper saleContractBidPaymentMapper() {
        return session.getMapper(MariaSaleContractBidPaymentMapper.class);
    }

    @Override
    public @NotNull SaleContractMapper saleContractMapper() {
        return session.getMapper(MariaSaleContractMapper.class);
    }

    @Override
    public @NotNull SaleContractOfferMapper saleContractOfferMapper() {
        return session.getMapper(MariaSaleContractOfferMapper.class);
    }

    @Override
    public @NotNull SaleContractOfferPaymentMapper saleContractOfferPaymentMapper() {
        return session.getMapper(MariaSaleContractOfferPaymentMapper.class);
    }

    @Override
    public @NotNull SaleContractSanctionedAuctioneerMapper saleContractSanctionedAuctioneerMapper() {
        return session.getMapper(MariaSaleContractSanctionedAuctioneerMapper.class);
    }

    @Override
    public void close() {
        session.close();
    }
}

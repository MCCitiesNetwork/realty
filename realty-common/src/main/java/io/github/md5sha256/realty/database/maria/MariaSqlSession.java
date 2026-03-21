package io.github.md5sha256.realty.database.maria;

import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.ContractMapper;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.LeaseHistoryMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractAuctionMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdHistoryMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractBidMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractOfferMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractBidPaymentMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractOfferPaymentMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractSanctionedAuctioneerMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaLeaseContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaRealtyRegionMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaLeaseHistoryMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaFreeholdContractAuctionMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaFreeholdHistoryMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaFreeholdContractBidMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaFreeholdContractMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaFreeholdContractOfferMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaFreeholdContractBidPaymentMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaFreeholdContractOfferPaymentMapper;
import io.github.md5sha256.realty.database.maria.mapper.MariaFreeholdContractSanctionedAuctioneerMapper;
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
    public @NotNull FreeholdHistoryMapper freeholdHistoryMapper() {
        return session.getMapper(MariaFreeholdHistoryMapper.class);
    }

    @Override
    public @NotNull LeaseHistoryMapper leaseHistoryMapper() {
        return session.getMapper(MariaLeaseHistoryMapper.class);
    }

    @Override
    public @NotNull FreeholdContractAuctionMapper freeholdContractAuctionMapper() {
        return session.getMapper(MariaFreeholdContractAuctionMapper.class);
    }

    @Override
    public @NotNull FreeholdContractBidMapper freeholdContractBidMapper() {
        return session.getMapper(MariaFreeholdContractBidMapper.class);
    }

    @Override
    public @NotNull FreeholdContractBidPaymentMapper freeholdContractBidPaymentMapper() {
        return session.getMapper(MariaFreeholdContractBidPaymentMapper.class);
    }

    @Override
    public @NotNull FreeholdContractMapper freeholdContractMapper() {
        return session.getMapper(MariaFreeholdContractMapper.class);
    }

    @Override
    public @NotNull FreeholdContractOfferMapper freeholdContractOfferMapper() {
        return session.getMapper(MariaFreeholdContractOfferMapper.class);
    }

    @Override
    public @NotNull FreeholdContractOfferPaymentMapper freeholdContractOfferPaymentMapper() {
        return session.getMapper(MariaFreeholdContractOfferPaymentMapper.class);
    }

    @Override
    public @NotNull FreeholdContractSanctionedAuctioneerMapper freeholdContractSanctionedAuctioneerMapper() {
        return session.getMapper(MariaFreeholdContractSanctionedAuctioneerMapper.class);
    }

    @Override
    public void close() {
        session.close();
    }
}

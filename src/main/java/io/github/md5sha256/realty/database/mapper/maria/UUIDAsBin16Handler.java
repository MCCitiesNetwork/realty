package io.github.md5sha256.realty.database.mapper.maria;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.nio.ByteBuffer;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@MappedTypes(UUID.class)
public class UUIDAsBin16Handler extends BaseTypeHandler<UUID> {


    /**
     * Convert a UUID to a 16-byte array.
     */
    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    /**
     * Convert a 16-byte array back to a UUID.
     */
    private static UUID fromBytes(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("Invalid UUID byte array length: " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long high = buffer.getLong();
        long low = buffer.getLong();
        return new UUID(high, low);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps,
                                    int i,
                                    UUID parameter,
                                    JdbcType jdbcType) throws SQLException {
        ps.setBytes(i, toBytes(parameter));
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromBytes(rs.getBytes(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromBytes(rs.getBytes(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromBytes(cs.getBytes(columnIndex));
    }
}

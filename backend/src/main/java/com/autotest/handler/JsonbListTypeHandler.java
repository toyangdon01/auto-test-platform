package com.autotest.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL JSONB List 类型处理器
 * 用于处理 List<Map<String, Object>> 类型的 JSONB 字段
 *
 * @author auto-test-platform
 */
@MappedTypes(List.class)
public class JsonbListTypeHandler extends BaseTypeHandler<List<Map<String, Object>>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Map<String, Object>> parameter, JdbcType jdbcType) throws SQLException {
        try {
            String json = objectMapper.writeValueAsString(parameter);
            // 使用 setObject 配合 Types.OTHER，让 PostgreSQL 自动转换
            ps.setObject(i, json, java.sql.Types.OTHER);
        } catch (JsonProcessingException e) {
            throw new SQLException("Error converting List to JSONB", e);
        }
    }

    @Override
    public List<Map<String, Object>> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }

    @Override
    public List<Map<String, Object>> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }

    @Override
    public List<Map<String, Object>> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }

    private List<Map<String, Object>> parseJson(String json) throws SQLException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            throw new SQLException("Error parsing JSONB to List", e);
        }
    }
}

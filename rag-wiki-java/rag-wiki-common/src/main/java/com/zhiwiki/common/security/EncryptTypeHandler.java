package com.zhiwiki.common.security;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 字段加密 TypeHandler
 * 
 * 自动对标注了 @FieldEncrypt 的字段进行加密/解密
 * 
 * 写入数据库时加密，读取时解密，业务代码无感知
 * 
 * 使用方式（在实体字段上）：
 * <pre>
 * &#64;TableField(typeHandler = EncryptTypeHandler.class)
 * &#64;FieldEncrypt
 * private String phone;
 * </pre>
 */
@Component
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    private static DataEncryptor dataEncryptor;

    /**
     * 通过Spring注入设置DataEncryptor实例
     * （TypeHandler由MyBatis创建，无法直接使用构造器注入）
     */
    public static void setDataEncryptor(DataEncryptor encryptor) {
        dataEncryptor = encryptor;
    }

    public EncryptTypeHandler() {
        // MyBatis需要无参构造器
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    /**
     * 加密
     */
    private String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        if (dataEncryptor == null) {
            return plaintext; // 未配置加密时原样返回
        }
        try {
            return dataEncryptor.encrypt(plaintext);
        } catch (Exception e) {
            return plaintext; // 加密失败时原样返回（降级策略）
        }
    }

    /**
     * 解密
     */
    private String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        if (dataEncryptor == null) {
            return ciphertext;
        }
        try {
            return dataEncryptor.decrypt(ciphertext);
        } catch (Exception e) {
            // 解密失败可能是明文数据（未开启加密前的历史数据），原样返回
            return ciphertext;
        }
    }
}

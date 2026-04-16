package com.zhiwiki.common.security;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具
 * 
 * 支持多种脱敏策略：
 * 1. 手机号脱敏 - 138****1234
 * 2. 身份证脱敏 - 110***********1234
 * 3. 邮箱脱敏 - t****e@example.com
 * 4. 姓名脱敏 - 张*、欧阳**
 * 5. 银行卡脱敏 - 6222 **** **** 1234
 * 6. 地址脱敏 - 北京市****
 * 7. 自定义脱敏
 * 
 * 使用示例：
 * <pre>
 * String masked = DataMasker.maskPhone("13812341234");     // 138****1234
 * String masked = DataMasker.maskEmail("test@example.com"); // t***e@example.com
 * String masked = DataMasker.mask("任意文本", 2, 2, '*');   // 任意****
 * </pre>
 */
public class DataMasker {

    private static final char DEFAULT_MASK_CHAR = '*';

    /** 手机号正则 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");

    /** 身份证正则 */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(\\d{3})\\d{11}(\\d{4})");

    /** 邮箱正则 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(\\w{1,2})\\w*(@\\w+\\.\\w+)");

    /** 银行卡正则 */
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("(\\d{4})\\d{8,12}(\\d{4})");

    /**
     * 手机号脱敏: 138****1234
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        Matcher matcher = PHONE_PATTERN.matcher(phone);
        if (matcher.matches()) {
            return matcher.group(1) + "****" + matcher.group(2);
        }
        // 非标准手机号，保留前3后4
        return mask(phone, 3, 4);
    }

    /**
     * 身份证脱敏: 110***********1234
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        Matcher matcher = ID_CARD_PATTERN.matcher(idCard);
        if (matcher.matches()) {
            return matcher.group(1) + "***********" + matcher.group(2);
        }
        return mask(idCard, 3, 4);
    }

    /**
     * 邮箱脱敏: t***e@example.com
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        Matcher matcher = EMAIL_PATTERN.matcher(email);
        if (matcher.matches()) {
            String prefix = matcher.group(1);
            String suffix = matcher.group(2);
            return prefix + "***" + suffix;
        }
        return mask(email, 1, email.indexOf('@'));
    }

    /**
     * 姓名脱敏:
     * - 两个字: 张* 
     * - 三个字: 张*明
     * - 四个字以上: 欧阳**明
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        int len = name.length();
        if (len == 1) {
            return name;
        } else if (len == 2) {
            return name.charAt(0) + String.valueOf(DEFAULT_MASK_CHAR);
        } else if (len == 3) {
            return name.charAt(0) + String.valueOf(DEFAULT_MASK_CHAR) + name.charAt(2);
        } else {
            return name.substring(0, 2) + repeat(DEFAULT_MASK_CHAR, len - 3) + name.charAt(len - 1);
        }
    }

    /**
     * 银行卡脱敏: 6222 **** **** 1234
     */
    public static String maskBankCard(String cardNo) {
        if (cardNo == null || cardNo.length() < 8) {
            return cardNo;
        }
        Matcher matcher = BANK_CARD_PATTERN.matcher(cardNo);
        if (matcher.matches()) {
            return matcher.group(1) + " **** **** " + matcher.group(2);
        }
        return mask(cardNo, 4, 4);
    }

    /**
     * 地址脱敏: 保留省市区，其余用****
     */
    public static String maskAddress(String address) {
        if (address == null || address.length() <= 6) {
            return address;
        }
        return address.substring(0, 6) + "****";
    }

    /**
     * 通用脱敏方法
     * 
     * @param data 原始数据
     * @param prefixLen 保留前缀长度
     * @param suffixLen 保留后缀长度
     * @return 脱敏后的数据
     */
    public static String mask(String data, int prefixLen, int suffixLen) {
        return mask(data, prefixLen, suffixLen, DEFAULT_MASK_CHAR);
    }

    /**
     * 通用脱敏方法
     * 
     * @param data 原始数据
     * @param prefixLen 保留前缀长度
     * @param suffixLen 保留后缀长度
     * @param maskChar 脱敏字符
     * @return 脱敏后的数据
     */
    public static String mask(String data, int prefixLen, int suffixLen, char maskChar) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        int len = data.length();
        if (len <= prefixLen + suffixLen) {
            // 数据太短，全部脱敏
            return repeat(maskChar, len);
        }

        String prefix = data.substring(0, prefixLen);
        String suffix = data.substring(len - suffixLen);
        int maskLen = len - prefixLen - suffixLen;
        return prefix + repeat(maskChar, maskLen) + suffix;
    }

    /**
     * 对文本中的所有敏感信息自动脱敏
     * 
     * @param text 原始文本
     * @return 自动脱敏后的文本
     */
    public static String autoMask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;
        // 手机号
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$2");
        // 身份证
        result = ID_CARD_PATTERN.matcher(result).replaceAll("$1***********$2");
        // 邮箱
        result = EMAIL_PATTERN.matcher(result).replaceAll("$1***$2");
        // 银行卡
        result = BANK_CARD_PATTERN.matcher(result).replaceAll("$1 **** **** $2");

        return result;
    }

    /**
     * 批量脱敏 - 对指定字段列表应用脱敏
     */
    public static List<String> maskList(List<String> data, int prefixLen, int suffixLen) {
        if (data == null) {
            return null;
        }
        return Arrays.asList(data.stream()
                .map(d -> mask(d, prefixLen, suffixLen))
                .toArray(String[]::new));
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}

package com.face.mymoney.ui;

import java.io.UnsupportedEncodingException;
/**
 * 拼音转换工具类，用于将股票的中文名称转换为首字母拼音或全拼，以支持便捷的股票拼音码搜索。
 */

public class PinyinUtils {
    private static final int[] pyValue = new int[]{
        45217, 45253, 45761, 46318, 46826, 47010, 47297, 47614, 48119, 49062,
        49324, 49831, 50372, 50614, 50622, 50906, 51387, 51446, 52218, 52698,
        52980, 53689, 54481
    };
    private static final char[] pyLetter = new char[]{
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k',
        'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'w',
        'x', 'y', 'z'
    };

    /**
     * 获取字符串的拼音首字母（英文字符保持不变，汉字转换为首字母）
     */
    public static String getFirstLetters(String str) {
        if (str == null || str.trim().length() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (ch >= 32 && ch <= 126) {
                sb.append(Character.toLowerCase(ch));
            } else {
                char letter = getFirstLetter(ch);
                if (letter != 0) {
                    sb.append(letter);
                } else {
                    sb.append(ch);
                }
            }
        }
        return sb.toString();
    }
    /**
     * 获取首个/第一个字母。
     */
    private static char getFirstLetter(char ch) {
        byte[] bytes;
        try {
            bytes = String.valueOf(ch).getBytes("GB2312");
        } catch (UnsupportedEncodingException e) {
            return 0;
        }
        if (bytes.length < 2) {
            return 0;
        }
        int value = ((bytes[0] & 0xFF) << 8) + (bytes[1] & 0xFF);
        if (value < 45217 || value > 55289) {
            return 0;
        }
        for (int i = 0; i < 23; i++) {
            if (value < pyValue[i]) {
                return pyLetter[i - 1];
            }
        }
        return 'z';
    }
}

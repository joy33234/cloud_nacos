package com.seektop.common.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmojiUtil {

    /**
     * 查看源字符串中是否包含emoji
     *
     * @param src
     * @return true:包含,false:不包含
     */
    public static Boolean hasEmoji(String src) {
        Pattern emoji = Pattern.compile("[\ud83c\udc00-\ud83c\udfff]|[\ud83d\udc00-\ud83d\udfff]|[\u2600-\u27ff]", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
        Matcher emojiMatcher = emoji.matcher(src);
        return emojiMatcher.find();
    }

}

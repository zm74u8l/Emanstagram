package com.emanstagram.common;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts @mentions and #hashtags from captions and comments. */
public final class TextTokens {

    // Mirrors the username rule in RegisterRequest. The lookbehind stops an
    // email address such as a@b.com from reading as a mention of "b.com".
    private static final Pattern MENTION = Pattern.compile("(?<![\\w@])@([A-Za-z0-9_.]{3,30})");
    private static final Pattern HASHTAG = Pattern.compile("(?<![\\w#])#([\\p{L}\\p{N}_]{1,60})");

    private TextTokens() {
    }

    public static Set<String> mentions(String text) {
        return collect(MENTION, text, true);
    }

    public static Set<String> hashtags(String text) {
        return collect(HASHTAG, text, false);
    }

    /** True when {@code text} contains exactly the tag, not merely a longer tag starting with it. */
    public static boolean hasTag(String text, String tag) {
        return hashtags(text).contains(tag.toLowerCase(Locale.ROOT));
    }

    private static Set<String> collect(Pattern pattern, String text, boolean stripTrailingDot) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1);
            // "thanks @emma." should mention emma, not "emma."
            while (stripTrailingDot && value.endsWith(".")) {
                value = value.substring(0, value.length() - 1);
            }
            if (!value.isEmpty()) {
                found.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return found;
    }
}

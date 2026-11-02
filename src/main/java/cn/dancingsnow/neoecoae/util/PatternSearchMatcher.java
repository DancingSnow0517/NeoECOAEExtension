package cn.dancingsnow.neoecoae.util;

import java.lang.reflect.Method;
import java.util.function.BiPredicate;

/** Optional JEC matching follows the player's pinyin, abbreviation and keyboard settings. */
public final class PatternSearchMatcher {
    private PatternSearchMatcher() { }

    private static final class Holder {
        static final BiPredicate<String, String> MATCHER = load(PatternSearchMatcher.class.getClassLoader());
    }

    public static boolean contains(String keywords, String term) {
        return Holder.MATCHER.test(keywords, term);
    }

    static BiPredicate<String, String> load(ClassLoader loader) {
        try {
            Method contains = Class.forName("me.towdium.jecharacters.utils.Match", false, loader)
                    .getMethod("contains", CharSequence.class, CharSequence.class);
            return (keywords, term) -> {
                if (keywords.contains(term)) return true;
                try {
                    return Boolean.TRUE.equals(contains.invoke(null, keywords, term));
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    return false;
                }
            };
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return String::contains;
        }
    }
}

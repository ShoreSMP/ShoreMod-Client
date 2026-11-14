package net.shoresmp.core;
import java.util.*;
import java.util.regex.Pattern;
public final class PunishmentMatcher {
    public static boolean matches(String message, List<String> phrases) {
        String plain = message.replaceAll("§[0-9A-FK-ORa-fk-or]", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return phrases.stream().filter(Objects::nonNull).map(s -> s.strip().toLowerCase(Locale.ROOT))
            .filter(s -> !s.isEmpty()).anyMatch(s -> Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(s) + "(?![\\p{L}\\p{N}_])").matcher(plain).find());
    }
}

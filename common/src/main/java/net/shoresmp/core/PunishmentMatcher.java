package net.shoresmp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class PunishmentMatcher {
    private static final Pattern FORMATTING = Pattern.compile("[\\u00a7\\u0015][0-9A-FK-ORXa-fk-orx]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private record Rules(List<String> phrases, List<Pattern> patterns) {}
    private static volatile Rules cached = new Rules(List.of(), List.of());

    public static boolean matches(String message, List<String> phrases) {
        if (message == null || phrases == null || phrases.isEmpty()) return false;
        Rules rules = cached;
        if (!rules.phrases.equals(phrases)) {
            // Copy the config because the phrase list can be edited in place.
            var copy = new ArrayList<>(phrases);
            var patterns = copy.stream().filter(Objects::nonNull).map(s -> s.strip().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).map(s -> Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(s) + "(?![\\p{L}\\p{N}_])")).toList();
            cached = rules = new Rules(copy, patterns);
        }
        String text = FORMATTING.matcher(message).replaceAll("").toLowerCase(Locale.ROOT);
        String plain = WHITESPACE.matcher(text).replaceAll(" ").trim();
        for (Pattern pattern : rules.patterns) if (pattern.matcher(plain).find()) return true;
        return false;
    }
}

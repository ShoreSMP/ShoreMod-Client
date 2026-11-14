package net.shoresmp.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conservatively separates personal punishment notices from staff broadcasts. */
public final class PunishmentTarget {
    private static final String PREFIX = "(?m)^\\s*(?:\\[[^\\]\\n]{1,64}\\]\\s*[:>»|\\-]*\\s*){0,4}";
    private static final String SELF = "(?:you(?:\\s+(?:are|were|have\\s+been|got)|'re|'ve\\s+been)|your\\s+account\\s+(?:has\\s+been|was|is))\\s+";
    private static final String ACTION = "(?:(?:temporarily|permanently)\\s+)?(?:banned|blacklisted|punished|suspended|kicked|disconnected)\\b";
    private static final Pattern PERSONAL = Pattern.compile(PREFIX + SELF + ACTION);
    private static final Pattern TRANSFER = Pattern.compile(PREFIX + SELF + "(?:sent|returned|moved)\\s+(?:back\\s+)?to\\s+(?:the\\s+)?(?:hub|lobby)\\b");
    private static final Pattern STAFF = Pattern.compile("(?m)^\\s*(?:\\[[^\\]\\n]{1,64}\\]\\s*){0,3}\\[(?:staff|staffchat|adminchat|modchat|punishment broadcast)\\b[^\\]\\n]*\\]");
    public static boolean matchesChat(String message,List<String> phrases,String localName) {
        if(message==null || phrases==null)return false;
        String plain=message.replaceAll("[\\u00a7\\u0015][0-9A-FK-ORXa-fk-orx]", "")
            .replace('\u2019','\'').toLowerCase(Locale.ROOT)
            .replaceAll("[\\t\\x0B\\f\\r ]+", " ").strip();
        if(!PunishmentMatcher.matches(plain,phrases) || STAFF.matcher(plain).find())return false;
        if(PERSONAL.matcher(plain).find() || TRANSFER.matcher(plain).find())return true;
        if(localName==null || !localName.matches("[A-Za-z0-9_]{1,16}"))return false;
        String name=Pattern.quote(localName.toLowerCase(Locale.ROOT));
        String boundary="[\\p{L}\\p{N}_]";
        // Being named as the punishing staff member ("banned by NAME") is not a match.
        if(Pattern.compile(PREFIX+"(?:player\\s+)?"+name+"\\s+(?:has\\s+been|was|is|got)\\s+"+ACTION).matcher(plain).find())return true;
        return Pattern.compile("(?m)^\\s*(?:target|player|punished player|banned player)\\s*[:=]\\s*"+name+"(?!"+boundary+")").matcher(plain).find();
    }
}

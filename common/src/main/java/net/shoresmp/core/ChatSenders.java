package net.shoresmp.core;

import java.util.*;
import java.util.regex.Pattern;

/** Resolves a sender header, never a player name mentioned in the message body. */
public final class ChatSenders {
    private static final Pattern FORMAT=Pattern.compile("[\\u00a7&][0-9a-fk-orx]",Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER=Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z0-9_]{1,16})\\s*(->|[:>\\u00bb\\u203a\\u2192\\u279c\\u276f\\u27a4\\u27a0\\u27a2\\u2022|])");
    private ChatSenders(){}

    public static String sender(String message,Collection<String> aliases){
        String plain=FORMAT.matcher(message).replaceAll("").replaceAll("\\[[^\\]]*\\]","");
        var matcher=HEADER.matcher(plain.substring(0,Math.min(256,plain.length())));
        while(matcher.find()){
            for(String alias:aliases)if(alias.equalsIgnoreCase(matcher.group(1)))return alias;
            // Rank separators may precede the sender. A colon/arrow already starts a body.
            if(!matcher.group(2).equals("|"))return null;
        }
        return null;
    }

    /** Tab-list display names may contain a rank and a server nickname. */
    public static String displayAlias(String displayName){
        String plain=FORMAT.matcher(displayName).replaceAll("").replaceAll("\\[[^\\]]*\\]","").strip();
        var matcher=Pattern.compile("[A-Za-z0-9_]{1,16}$").matcher(plain);
        return matcher.find()&&(matcher.start()==0||!Character.isLetterOrDigit(plain.charAt(matcher.start()-1)))?matcher.group():null;
    }
}


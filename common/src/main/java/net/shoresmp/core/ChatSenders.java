package net.shoresmp.core;
import java.util.*;
import java.util.regex.Pattern;
/** Resolves formatted sender headers and preserves the original name position. */
public final class ChatSenders {
 private static final Pattern FORMAT=Pattern.compile("[\\u00a7&][0-9a-fk-orx]",Pattern.CASE_INSENSITIVE);
 private static final Pattern HEADER=Pattern.compile("(?<![A-Za-z0-9_.])([.]?[A-Za-z0-9_]{1,16})\\s*(->|[:>\\u00bb\\u203a\\u2192\\u279c\\u276f\\u27a4\\u27a0\\u27a2\\u2022|])");
 public record Match(String alias,int start,int end){}
 private ChatSenders(){}
 public static Match match(String message,Collection<String> aliases){
  String plain=FORMAT.matcher(message).replaceAll("  ");
  int newline=plain.indexOf('\n'),limit=Math.min(256,newline<0?plain.length():newline);
  String header=plain.substring(0,limit);
  var matcher=HEADER.matcher(header);
  while(matcher.find()){
   // Ignore timestamp/rank brackets without losing source character offsets.
   int open=header.lastIndexOf('[',matcher.start()),closed=header.lastIndexOf(']',matcher.start());
   if(open>closed&&header.indexOf(']',matcher.end())>=0)continue;
   for(String alias:aliases)if(alias.equalsIgnoreCase(matcher.group(1)))return new Match(alias,matcher.start(1),matcher.end(1));
   if(!matcher.group(2).equals("|"))return null;
  }
  if(newline>=0){
   Match best=null;
   for(String alias:aliases){
    var token=Pattern.compile("(?<![A-Za-z0-9_.])"+Pattern.quote(alias)+"(?![A-Za-z0-9_])",Pattern.CASE_INSENSITIVE).matcher(header);
    while(token.find()){
     if(header.substring(token.end()).matches("[^A-Za-z0-9_]*")&&(best==null||token.start()>best.start()))best=new Match(alias,token.start(),token.end());
    }
   }
   return best;
  }
  return null;
 }
 public static String sender(String message,Collection<String> aliases){var match=match(message,aliases);return match==null?null:match.alias();}
 public static String displayAlias(String name){
  String plain=FORMAT.matcher(name).replaceAll("").replaceAll("\\[[^\\]]*\\]","").strip();
  var matcher=Pattern.compile("[.]?[A-Za-z0-9_]{1,16}$").matcher(plain);
  return matcher.find()&&(matcher.start()==0||!Character.isLetterOrDigit(plain.charAt(matcher.start()-1)))?matcher.group():null;
 }
}

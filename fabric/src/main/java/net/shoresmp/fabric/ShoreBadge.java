package net.shoresmp.fabric;

import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ShoreBadge {
    private static final Style LOGO_STYLE=Style.EMPTY
        .withFont(new StyleSpriteSource.Font(Identifier.of("shoremod","badge")))
        .withColor(0xFFFFFF);
    private static final Style TAB_STYLE=Style.EMPTY
        .withFont(new StyleSpriteSource.Font(Identifier.of("shoremod","badge_tab")))
        .withColor(0xFFFFFF);
    private static final java.util.Map<Text,Text> TAB_CACHE=cache(),NAME_CACHE=cache();
    private static java.util.Map<Text,Text> cache(){return new java.util.LinkedHashMap<>(256,0.75f,true){protected boolean removeEldestEntry(java.util.Map.Entry<Text,Text> e){return size()>512;}};}
    /** Local branding does not require the server presence protocol. */
    public static boolean isLocal(java.util.UUID id) {
        var player=net.minecraft.client.MinecraftClient.getInstance().player;
        return id!=null&&player!=null&&id.equals(player.getUuid());
    }
    public static boolean hasBadge(java.util.UUID id) {
        return id!=null&&(isLocal(id)||ShoreClient.roster.users().containsKey(id));
    }
    public static Text prependTab(Text label) {
        return TAB_CACHE.computeIfAbsent(label,key->Text.empty().append(Text.literal("\uE000").setStyle(TAB_STYLE)).append(" ").append(key));
    }
    public static Text prepend(Text label) {
        return NAME_CACHE.computeIfAbsent(label,key->Text.empty().append(Text.literal("\uE000").setStyle(LOGO_STYLE)).append(" ").append(key));
    }
}

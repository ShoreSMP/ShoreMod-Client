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
    public static Text prependTab(Text label) {
        return Text.empty().append(Text.literal("\uE000").setStyle(TAB_STYLE)).append(" ").append(label);
    }
    public static Text prepend(Text label) {
        return Text.empty().append(Text.literal("\uE000").setStyle(LOGO_STYLE)).append(" ").append(label);
    }
}

package net.shoresmp.core;
/** Stable session-specific visual variation; this never changes world generation or block positions. */
public final class TextureSeed {
    private TextureSeed(){}
    public static long scramble(long position,long salt){
        long v=position^salt;
        v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;
        v=(v^(v>>>27))*0x94d049bb133111ebL;
        return v^(v>>>31);
    }
}

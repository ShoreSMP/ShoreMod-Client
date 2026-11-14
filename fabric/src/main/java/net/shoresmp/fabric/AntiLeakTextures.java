package net.shoresmp.fabric;
import java.security.SecureRandom;
import net.shoresmp.core.TextureSeed;
public final class AntiLeakTextures {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static volatile long salt=RANDOM.nextLong();
    private AntiLeakTextures(){}
    public static void reshuffle(){salt=RANDOM.nextLong();}
    public static long seed(long position){return TextureSeed.scramble(position,salt);}
}

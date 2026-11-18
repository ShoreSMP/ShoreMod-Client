package net.shoresmp.fabric;

import net.minecraft.client.MinecraftClient;

public final class ShoreWorldView {
    private ShoreWorldView() {}

    /** Only the client's visual time sampler reads this value. Server and HUD clocks remain live. */
    public static long visualTime(long serverTime) {
        Config c = ShoreClient.config;
        if (c == null || !c.timeChanger) return serverTime;
        var client = MinecraftClient.getInstance();
        if (client == null || !client.isOnThread() || client.world == null || !client.world.getDimension().hasSkyLight()) return serverTime;
        int ticks = switch (c.timePreset) {
            case 0 -> 1000;
            case 1 -> 6000;
            case 2 -> 12500;
            case 3 -> 18000;
            default -> c.timeCustom;
        };
        return serverTime - Math.floorMod(serverTime, 24000L) + ticks;
    }
}

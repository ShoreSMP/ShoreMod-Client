package net.shoresmp.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;

/** Only client-extracted mob models are eligible. No entity scan or second model pass. */
public final class ShoreMobOverlay {
    private ShoreMobOverlay() {}

    public interface State {
        int shore$getFill();
        void shore$setFill(int color);
    }

    public static boolean selected(Entity entity) {
        var c = ShoreClient.config;
        if (c == null || !c.mobOverlay || !(entity instanceof MobEntity) || entity.isInvisible() || !entity.isAlive()) return false;
        var mc = MinecraftClient.getInstance();
        if (mc.player == null || !c.mobAlways && mc.targetedEntity != entity) return false;
        return entity.squaredDistanceTo(mc.player) <= (double) c.mobRange * c.mobRange;
    }

    public static int fill(Entity entity) {
        var c = ShoreClient.config;
        if (c == null || !c.mobOverlay || c.mobMode == 0 || !selected(entity)) return 0;
        int rgb = c.mobFillColor, amount = c.mobFillStrength;
        return 0xFF000000 | channel(rgb >> 16, amount) << 16 | channel(rgb >> 8, amount) << 8 | channel(rgb, amount);
    }

    private static int channel(int value, int strength) {
        return 255 - (255 - (value & 255)) * strength / 100;
    }

    static void settings(ShoreScreen s) {
        var c = ShoreClient.config;
        s.section("Appearance");
        s.segment("Mode", "", new String[]{"Outline", "Full"}, () -> c.mobMode, v -> c.mobMode = v);
        s.color("Outline color", "", () -> c.mobOutlineColor, v -> c.mobOutlineColor = v);
        s.color("Fill color", "Tint the mob's texture in Full mode", () -> c.mobFillColor, v -> c.mobFillColor = v);
        s.slider("Fill strength", "", 0, 100, 1, "%", () -> c.mobFillStrength, v -> c.mobFillStrength = v);
        s.section("Visibility");
        s.toggle("Show always", "Off highlights only the mob under your crosshair", () -> c.mobAlways, v -> c.mobAlways = v);
        s.slider("Range", "Maximum distance from you", 4, 256, 1, " m", () -> c.mobRange, v -> c.mobRange = v);
    }
}

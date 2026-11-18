package net.shoresmp.fabric;

import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.*;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.shoresmp.core.MenuImages;

/** One local texture, decoded on demand outside the render thread. */
final class ShoreMenuBackground {
    private static final Identifier ID = Identifier.of("shoremod", "dyn/menu_background");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("shoremod/menu-background.png");
    private static int width, height, generation;
    private static boolean busy, attempted;
    private static String lastError = "";

    private ShoreMenuBackground() {}

    static String status() {
        if (busy) return "Loading image...";
        if (!lastError.isEmpty()) return lastError;
        if (width > 0) return width + " x " + height;
        return ShoreClient.config.menuImage ? "Loading on next frame" : "Hidden";
    }

    static void importImage(Path path) {
        MinecraftClient.getInstance().execute(() -> load(path, true));
    }

    private static void load(Path source, boolean importing) {
        if (busy) {
            if (importing) ShoreClient.tellError("An image is still loading. Try again in a moment.");
            return;
        }
        busy = true;
        attempted = true;
        lastError = "";
        int request = generation;
        var client = MinecraftClient.getInstance();
        Thread worker = new Thread(() -> {
            Path temp = null;
            NativeImage pixels = null;
            try {
                var image = MenuImages.read(source);
                try {
                    if (importing) {
                        Files.createDirectories(FILE.getParent());
                        temp = Files.createTempFile(FILE.getParent(), "menu-", ".png");
                        if (!ImageIO.write(image, "png", temp.toFile())) throw new IOException("PNG encoder unavailable");
                    }
                    int w = image.getWidth(), h = image.getHeight();
                    pixels = new NativeImage(w, h, false);
                    int[] argb = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) pixels.setColorArgb(x, y, argb[y*w+x]);
                    }
                } finally {
                    image.flush();
                }
                NativeImage ready = pixels;
                Path staged = temp;
                client.execute(() -> install(request, ready, staged));
                pixels = null;
                temp = null;
            } catch (Exception error) {
                client.execute(() -> {
                    if (generation != request) return;
                    busy = false;
                    lastError = "Import failed";
                    ShoreClient.tellError("Could not load menu background. " + error.getMessage());
                });
            } finally {
                if (pixels != null) pixels.close();
                discard(temp);
            }
        }, "ShoreMod menu image");
        worker.setDaemon(true);
        worker.start();
    }

    private static void install(int request, NativeImage pixels, Path staged) {
        if (generation != request) {
            pixels.close();
            discard(staged);
            return;
        }
        boolean transferred = false;
        NativeImageBackedTexture texture = null;
        try {
            texture = new NativeImageBackedTexture(() -> "ShoreMod menu background", pixels);
            if (staged != null) {
                try { Files.move(staged, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(staged, FILE, StandardCopyOption.REPLACE_EXISTING); }
            }
            MinecraftClient.getInstance().getTextureManager().registerTexture(ID, texture);
            transferred = true;
            width = pixels.getWidth();
            height = pixels.getHeight();
            if (staged != null) {
                ShoreClient.config.menuImage = true;
                ShoreClient.config.save();
                ShoreClient.tell("Menu background imported.");
            }
        } catch (Exception error) {
            lastError = "Texture unavailable";
            ShoreClient.tellError("Could not apply menu background. " + error.getMessage());
        } finally {
            if (texture == null) pixels.close(); else if (!transferred) texture.close();
            discard(staged);
            busy = false;
        }
    }

    private static void discard(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); }
        catch (IOException error) { ShoreClient.LOG.warn("Could not remove temporary background", error); }
    }

    static void clear() {
        generation++;
        busy = false;
        attempted = false;
        lastError = "";
        if (width > 0) MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);
        width = height = 0;
    }

    static void draw(ShoreUi ui, Config c) {
        if (!c.menuImage || c.menuImageOpacity == 0) return;
        if (!attempted) load(FILE, false);
        if (width <= 0 || height <= 0) return;
        int viewportW = ui.p(ui.W), viewportH = ui.p(ui.H);
        int w = viewportW, h = viewportH;
        if (c.menuImageFit != 2) {
            float sx = viewportW / (float)width, sy = viewportH / (float)height;
            float scale = c.menuImageFit == 0 ? Math.max(sx, sy) : Math.min(sx, sy);
            w = Math.max(1, Math.round(width * scale));
            h = Math.max(1, Math.round(height * scale));
        }
        int tint = Math.round(c.menuImageOpacity * 2.55f) << 24 | c.menuImageTint;
        ui.ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ID, (viewportW-w)/2, (viewportH-h)/2, 0f, 0f, w, h, w, h, tint);
    }
}

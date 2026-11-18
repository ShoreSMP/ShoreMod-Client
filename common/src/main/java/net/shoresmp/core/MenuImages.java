package net.shoresmp.core;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class MenuImages {
    private MenuImages() {}

    /** Inspect the PNG header before allocating pixel storage, then downsample wide photos. */
    public static BufferedImage read(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 24L * 1024 * 1024) {
            throw new IOException("Choose a PNG under 24 MiB");
        }
        try (var input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) throw new IOException("Could not open image");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Choose a PNG image");
            var reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase("png")) throw new IOException("Choose a PNG image");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 16384 || height > 16384 || (long) width * height > 24_000_000) {
                    throw new IOException("Choose an image up to 24 megapixels and 16384 pixels per side");
                }
                var options = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(width, height) / 2048);
                options.setSourceSubsampling(sample, sample, 0, 0);
                var source = reader.read(0, options);
                double factor = Math.min(1, 2048.0 / Math.max(width, height));
                var image = new BufferedImage(Math.max(1, (int)Math.round(width * factor)),
                    Math.max(1, (int)Math.round(height * factor)), BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, 0, 0, image.getWidth(), image.getHeight(), null);
                } finally {
                    graphics.dispose();
                    source.flush();
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }
}

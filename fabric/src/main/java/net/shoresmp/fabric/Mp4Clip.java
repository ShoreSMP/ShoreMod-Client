package net.shoresmp.fabric;

import net.shoresmp.core.FrameRing;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Rational;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.List;

/** Offline H.264 export. Called only by the clip export worker, never the render thread. */
public final class Mp4Clip {
    public static void write(Path target, List<FrameRing.Frame> frames, int fps, long end) throws IOException {
        if (frames.isEmpty()) throw new IOException("No gameplay frames buffered yet");
        if (fps < 1 || fps > 60) throw new IllegalArgumentException("FPS must be 1–60");
        long start = frames.getFirst().nanos();
        int count = Math.max(1, (int)Math.ceil(Math.min(30, Math.max(0, (end-start)/1e9))*fps));
        boolean complete = false;
        try (var channel = NIOUtils.writableChannel(target.toFile())) {
            var encoder = new AWTSequenceEncoder(channel, Rational.R(fps, 1));
            int index = 0, decodedIndex = -1;
            BufferedImage decoded = null;
            for (int i=0; i<count; i++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Clip export interrupted");
                long time = start + i*1_000_000_000L/fps;
                while (index+1<frames.size() && frames.get(index+1).nanos()<=time) index++;
                if (index!=decodedIndex) {
                    decoded = ImageIO.read(new ByteArrayInputStream(frames.get(index).jpeg()));
                    if (decoded==null) throw new IOException("Invalid buffered clip frame");
                    decodedIndex = index;
                }
                encoder.encodeImage(decoded);
            }
            encoder.finish();
            complete = true;
        } finally {
            if (!complete) Files.deleteIfExists(target);
        }
    }
}

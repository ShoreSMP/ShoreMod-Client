package net.shoresmp.core;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Writes a standard indexed MJPEG AVI without external codecs or screen-capture processes. */
public final class MjpegAvi {
    public static void write(Path target, List<FrameRing.Frame> source, int fps, long endNanos) throws IOException {
        if (source.isEmpty()) throw new IOException("No gameplay frames buffered yet");
        if (fps < 1 || fps > 60) throw new IllegalArgumentException("FPS");
        int width = source.getFirst().width(), height = source.getFirst().height();
        long start = source.getFirst().nanos();
        int count = Math.max(1, Math.min(fps * 30, (int)Math.ceil((endNanos - start) / 1_000_000_000.0 * fps)));
        var frames = new ArrayList<FrameRing.Frame>();
        int index = 0;
        for (int i = 0; i < count; i++) {
            long time = start + i * 1_000_000_000L / fps;
            while (index + 1 < source.size() && source.get(index + 1).nanos() <= time) index++;
            frames.add(source.get(index));
        }
        int max = source.stream().mapToInt(f -> f.jpeg().length).max().orElseThrow();
        try (var out = new RandomAccessFile(target.toFile(), "rw")) {
            out.setLength(0);
            four(out, "RIFF"); long riffSize = out.getFilePointer(); le(out, 0); four(out, "AVI ");
            long hdrl = list(out, "hdrl");
            four(out, "avih"); le(out, 56); le(out, 1_000_000 / fps); le(out, max * fps); le(out, 0);
            le(out, 0x10); le(out, count); le(out, 0); le(out, 1); le(out, max); le(out, width); le(out, height);
            for (int i = 0; i < 4; i++) le(out, 0);
            long strl = list(out, "strl");
            four(out, "strh"); le(out, 56); four(out, "vids"); four(out, "MJPG"); le(out, 0);
            shortLE(out, 0); shortLE(out, 0); le(out, 0); le(out, 1); le(out, fps); le(out, 0);
            le(out, count); le(out, max); le(out, -1); le(out, 0);
            shortLE(out, 0); shortLE(out, 0); shortLE(out, width); shortLE(out, height);
            four(out, "strf"); le(out, 40); le(out, 40); le(out, width); le(out, height);
            shortLE(out, 1); shortLE(out, 24); four(out, "MJPG"); le(out, width * height * 3);
            for (int i = 0; i < 4; i++) le(out, 0);
            endList(out, strl); endList(out, hdrl);
            long movi = list(out, "movi");
            var offsets = new ArrayList<Integer>();
            for (var frame : frames) {
                offsets.add(Math.toIntExact(out.getFilePointer() - (movi + 4)));
                four(out, "00dc"); le(out, frame.jpeg().length); out.write(frame.jpeg());
                if ((frame.jpeg().length & 1) != 0) out.write(0);
            }
            endList(out, movi);
            four(out, "idx1"); le(out, frames.size() * 16);
            for (int i = 0; i < frames.size(); i++) {
                four(out, "00dc"); le(out, 0x10); le(out, offsets.get(i)); le(out, frames.get(i).jpeg().length);
            }
            long length = out.length(); out.seek(riffSize); le(out, Math.toIntExact(length - 8));
        }
    }
    private static long list(RandomAccessFile out, String type) throws IOException {
        four(out, "LIST"); long size = out.getFilePointer(); le(out, 0); four(out, type); return size;
    }
    private static void endList(RandomAccessFile out, long at) throws IOException {
        long end = out.getFilePointer(); out.seek(at); le(out, Math.toIntExact(end - at - 4)); out.seek(end);
    }
    private static void four(RandomAccessFile out, String text) throws IOException { out.writeBytes(text); }
    private static void le(RandomAccessFile out, int value) throws IOException { out.writeInt(Integer.reverseBytes(value)); }
    private static void shortLE(RandomAccessFile out, int value) throws IOException { out.writeShort(Short.reverseBytes((short)value)); }
}

package net.shoresmp.core;

import java.io.*;
import java.util.*;

/** A bounded binary protocol shared by Fabric payloads and Bukkit plugin messages. */
public final class Wire {
    public static final String CHANNEL = "shoremod:wire";
    public static final int VERSION = 1, HELLO = 0, ROSTER = 1, CLIP = 2;
    public static final int MAX_BYTES = 30000, PAGE_SIZE = 500;
    public record User(UUID id, String name) {
        public User {
            Objects.requireNonNull(id);
            if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid player name");
        }
    }
    public record Message(int op, long generation, int page, int pages, List<User> users, String reason) {}
    private interface Writer { void write(DataOutputStream out) throws IOException; }
    private static byte[] encode(int op, Writer writer) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeByte(VERSION); out.writeByte(op); writer.write(out); out.flush();
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Message too large");
            return bytes.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static byte[] hello() { return encode(HELLO, out -> {}); }
    /** Only this exact constant-size message is accepted from a client by the server. */
    public static boolean isHello(byte[] bytes) { return bytes != null && bytes.length == 2 && bytes[0] == VERSION && bytes[1] == HELLO; }
    public static byte[] clip(String reason) {
        return encode(CLIP, out -> out.writeUTF(reason.substring(0, Math.min(1000, reason.length()))));
    }
    public static List<byte[]> roster(long generation, Collection<User> users) {
        var list = users.stream().sorted(Comparator.comparing(u -> u.id().toString())).toList();
        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (pages > 128) throw new IllegalArgumentException("Too many users");
        var result = new ArrayList<byte[]>();
        for (int p = 0; p < pages; p++) {
            final int page = p;
            var slice = list.subList(p * PAGE_SIZE, Math.min(list.size(), (p + 1) * PAGE_SIZE));
            result.add(encode(ROSTER, out -> {
                out.writeLong(generation); out.writeShort(page); out.writeShort(pages); out.writeShort(slice.size());
                for (User user : slice) {
                    out.writeLong(user.id().getMostSignificantBits()); out.writeLong(user.id().getLeastSignificantBits());
                    out.writeUTF(user.name());
                }
            }));
        }
        return List.copyOf(result);
    }
    public static Message decode(byte[] data) throws IOException {
        if (data.length < 2 || data.length > MAX_BYTES) throw new IOException("Invalid payload length");
        var in = new DataInputStream(new ByteArrayInputStream(data));
        if (in.readUnsignedByte() != VERSION) throw new IOException("Protocol version mismatch");
        int op = in.readUnsignedByte();
        long generation = 0; int page = 0, pages = 0; String reason = "";
        var users = new ArrayList<User>();
        if (op == ROSTER) {
            generation = in.readLong(); page = in.readUnsignedShort(); pages = in.readUnsignedShort();
            int count = in.readUnsignedShort();
            if (pages < 1 || pages > 128 || page >= pages || count > PAGE_SIZE) throw new IOException("Invalid roster page");
            try {
                for (int i = 0; i < count; i++) users.add(new User(new UUID(in.readLong(), in.readLong()), in.readUTF()));
            } catch (IllegalArgumentException e) { throw new IOException(e); }
        } else if (op == CLIP) {
            reason = in.readUTF(); if (reason.length() > 1000) throw new IOException("Reason too long");
        } else if (op != HELLO) throw new IOException("Unknown opcode");
        if (in.available() != 0) throw new IOException("Trailing payload data");
        return new Message(op, generation, page, pages, List.copyOf(users), reason);
    }
}

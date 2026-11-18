package net.shoresmp.fabric;

import java.nio.charset.StandardCharsets;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Raw UTF-8 JSON, also readable by Paper's plugin messenger. */
public record GamesPayload(String json) implements CustomPayload {
    public static final Id<GamesPayload> ID = new Id<>(Identifier.of("shoremod", "games"));
    private static final int MAX_BYTES = 24 * 1024;
    public static final PacketCodec<RegistryByteBuf, GamesPayload> CODEC = new PacketCodec<>() {
        @Override public GamesPayload decode(RegistryByteBuf buf) {
            int length = buf.readableBytes();
            if (length > MAX_BYTES) throw new IllegalArgumentException("Game message too large");
            return new GamesPayload(buf.readCharSequence(length, StandardCharsets.UTF_8).toString());
        }
        @Override public void encode(RegistryByteBuf buf, GamesPayload payload) {
            byte[] bytes = payload.json.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Game message too large");
            buf.writeBytes(bytes);
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}

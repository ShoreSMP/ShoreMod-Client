package net.shoresmp.fabric;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shoresmp.core.Wire;

public record ShorePayload(byte[] bytes) implements CustomPayload {
    public static final Id<ShorePayload> ID = new Id<>(Identifier.of(Wire.CHANNEL));
    public static final PacketCodec<RegistryByteBuf, ShorePayload> CODEC = new PacketCodec<>() {
        @Override public ShorePayload decode(RegistryByteBuf buf) {
            int length = buf.readableBytes();
            if (length > Wire.MAX_BYTES) throw new IllegalArgumentException("ShoreMod payload too large");
            byte[] bytes = new byte[length]; buf.readBytes(bytes); return new ShorePayload(bytes);
        }
        @Override public void encode(RegistryByteBuf buf, ShorePayload payload) {
            if (payload.bytes.length > Wire.MAX_BYTES) throw new IllegalArgumentException("ShoreMod payload too large");
            buf.writeBytes(payload.bytes);
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}

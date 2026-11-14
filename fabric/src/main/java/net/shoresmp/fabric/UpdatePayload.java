package net.shoresmp.fabric;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shoresmp.core.ModUpdates;

public record UpdatePayload(byte[] bytes) implements CustomPayload {
    public static final Id<UpdatePayload> ID = new Id<>(Identifier.of(ModUpdates.CHANNEL));
    public static final PacketCodec<RegistryByteBuf, UpdatePayload> CODEC = new PacketCodec<>() {
        @Override public UpdatePayload decode(RegistryByteBuf buf) {
            int length = buf.readableBytes();
            if (length > 4096) throw new IllegalArgumentException("ShoreMod payload too large");
            byte[] bytes = new byte[length]; buf.readBytes(bytes); return new UpdatePayload(bytes);
        }
        @Override public void encode(RegistryByteBuf buf, UpdatePayload payload) {
            if (payload.bytes.length > 4096) throw new IllegalArgumentException("ShoreMod payload too large");
            buf.writeBytes(payload.bytes);
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}

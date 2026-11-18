package net.shoresmp.fabric;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Separate advertised capability for replies to explicit server-side menu commands. */
public record GamesUiPayload(String json) implements CustomPayload {
    public static final Id<GamesUiPayload> ID = new Id<>(Identifier.of("shoremod", "games_ui"));
    public static final PacketCodec<RegistryByteBuf, GamesUiPayload> CODEC =
        GamesPayload.CODEC.xmap(value -> new GamesUiPayload(value.json()), value -> new GamesPayload(value.json()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}

package net.shoresmp.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;

final class ShoreGames {
    enum Kind { GIVEAWAY, TICTACTOE }
    record Giveaway(String id, String code, String host, String reward, boolean custom, long ends, List<String> names,
                    boolean joined, boolean finished, String winner) {}
    record Match(String id, String x, String o, String stake, String stage, String turn,
                 String you, String board, String message, long expires) {}

    static void register() {
        PayloadTypeRegistry.playC2S().register(GamesPayload.ID, GamesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GamesPayload.ID, GamesPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(GamesPayload.ID, (packet, context) -> {
            var mc = context.client();
            mc.execute(() -> {
                if (!(mc.currentScreen instanceof ShoreGameScreen screen)) return;
                try {
                    screen.receive(JsonParser.parseString(packet.json()).getAsJsonObject());
                } catch (RuntimeException e) {
                    screen.error("The server sent an unreadable game update. Refresh to try again.");
                }
            });
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
            dispatcher.register(ClientCommandManager.literal("shoremod")
                .then(ClientCommandManager.literal("giveaway")
                    .executes(ctx -> open(Kind.GIVEAWAY, ""))
                    .then(ClientCommandManager.argument("code", StringArgumentType.word())
                        .executes(ctx -> open(Kind.GIVEAWAY, StringArgumentType.getString(ctx, "code")))))
                .then(ClientCommandManager.literal("tictactoe").executes(ctx -> open(Kind.TICTACTOE, "")))));
    }

    private static int open(Kind kind, String code) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null) return 0;
        ShoreClient.openNextTick(() -> new ShoreGameScreen(kind, code));
        return 1;
    }

    static boolean send(JsonObject request) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null || !ClientPlayNetworking.canSend(GamesPayload.ID)) return false;
        ClientPlayNetworking.send(new GamesPayload(request.toString()));
        return true;
    }

    static JsonObject request(String op) {
        var request = new JsonObject();
        request.addProperty("op", op);
        return request;
    }

    static String string(JsonObject data, String key, int limit) {
        if (!data.has(key) || !data.get(key).isJsonPrimitive()) throw new IllegalArgumentException(key);
        String value = data.get(key).getAsString();
        if (value.length() > limit || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(key);
        return value;
    }

    static Giveaway giveaway(JsonObject data) {
        var names = new ArrayList<String>();
        var array = data.getAsJsonArray("names");
        if (array.size() > 400 || array.size() != data.get("entries").getAsInt()) throw new IllegalArgumentException("entries");
        for (var name : array) {
            String id = name.getAsString();
            if (id.isEmpty() || id.length() > 32 || id.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("name");
            names.add(id);
        }
        String winner = string(data, "winner", 32);
        if (!winner.isEmpty() && !names.contains(winner)) throw new IllegalArgumentException("winner");
        return new Giveaway(string(data, "id", 64), string(data, "code", 24), string(data, "host", 32), string(data, "reward", 80),
            "custom".equals(string(data,"rewardType",8)), data.get("ends").getAsLong(), List.copyOf(names), data.get("joined").getAsBoolean(),
            data.get("finished").getAsBoolean(), winner);
    }

    static Match match(JsonObject data) {
        String board = string(data, "board", 9);
        String stage = string(data, "stage", 8);
        String you = string(data, "you", 1);
        String turn = string(data, "turn", 1);
        if (!board.matches("[XO.]{9}") || !List.of("invite", "play", "done").contains(stage)
            || !List.of("X", "O").contains(you) || !List.of("X", "O").contains(turn)) throw new IllegalArgumentException("board");
        return new Match(string(data, "id", 64), string(data, "x", 32), string(data, "o", 32),
            string(data, "stake", 64), stage, turn, you, board, string(data, "message", 240), data.get("expires").getAsLong());
    }
}

package net.shoresmp.fabric;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Locale;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;

/** Local game shortcuts share the server's command name without claiming its dispatcher. */
public final class ShoreCommandRouting {
    private ShoreCommandRouting() {}
    private static boolean serverRoot;
    private static Action pendingServerAction;
    private static long pendingAt;
    private record Action(String verb, String code) {}

    static void register() { ClientSendMessageEvents.ALLOW_COMMAND.register(ShoreCommandRouting::allow); }

    static void clear() { serverRoot = false; pendingServerAction = null; }

    /** Modify the attached node, preserving server-provided permissions, arguments and completion providers. */
    public static <S> void addSuggestions(CommandDispatcher<S> dispatcher) {
        CommandNode<S> root = dispatcher.getRoot().getChild("shoremod");
        serverRoot = root != null;
        if (root == null) root = dispatcher.register(LiteralArgumentBuilder.<S>literal("shoremod").executes(context -> 1));
        for (String name : new String[]{"giveaway", "tictactoe", "clip", "help", "status"}) {
            if (root.getChild(name) != null) continue;
            var branch = LiteralArgumentBuilder.<S>literal(name).executes(context -> 1);
            if (name.equals("giveaway")) {
                branch.then(RequiredArgumentBuilder.<S,String>argument("code", StringArgumentType.word()).executes(context -> 1));
            } else if (name.equals("clip")) {
                // Bukkit's legacy tree delegates arbitrary arguments to its tab completer.
                // Keep that provider for staff targeting players after the self-clip literal.
                for (var child : root.getChildren()) if (child instanceof ArgumentCommandNode<?,?>) branch.then(child);
            }
            root.addChild(branch.build());
        }
    }

    private static boolean allow(String command) {
        if (!command.regionMatches(true, 0, "shoremod", 0, 8)) return true;
        String[] words = command.strip().split("\\s+", 4);
        boolean serverNamespace = words[0].equalsIgnoreCase("shoremod:shoremod");
        if (!serverNamespace && !words[0].equalsIgnoreCase("shoremod")) return true;
        String verb = words.length < 2 ? "" : words[1].toLowerCase(Locale.ROOT);
        Action action = switch (verb) {
            case "giveaway" -> words.length <= 3 ? new Action(verb, words.length == 3 ? words[2] : "") : null;
            case "tictactoe", "clip" -> words.length == 2 ? new Action(verb, "") : null;
            default -> null;
        };
        pendingServerAction = null;
        if (serverNamespace) {
            pendingServerAction = action;
            pendingAt = System.nanoTime();
            return true;
        }
        if (action != null) { run(action); return false; }
        if (words.length <= 2 && (verb.isEmpty() || verb.equals("help") || verb.equals("status"))) {
            if (verb.equals("status")) {
                ShoreClient.tell("Client " + ShoreClient.version() + " | " + ShoreClient.presenceStatus()
                    + " | Multiplayer games " + (ShoreGames.available() ? "available" : "unavailable on this server"));
                ShoreClient.resyncPresence();
            } else {
                ShoreClient.tell("Host or join: /shoremod giveaway [code] | Play: /shoremod tictactoe | Save your clip: /shoremod clip | Settings: /shore");
            }
            return serverRoot;
        }
        return true;
    }

    /** A namespaced server command can reply once to the player's matching request. */
    static void receiveServerAction(JsonObject packet) {
        Action expected = pendingServerAction;
        if (expected == null || System.nanoTime() - pendingAt > 10_000_000_000L) return;
        String op = ShoreGames.string(packet, "op", 12);
        Action action;
        if ("clip".equals(op)) action = new Action("clip", "");
        else if ("open".equals(op)) action = new Action(ShoreGames.string(packet, "game", 12), ShoreGames.string(packet, "code", 24));
        else return;
        if (!action.verb.equals(expected.verb) || !action.code.equalsIgnoreCase(expected.code)) return;
        pendingServerAction = null;
        run(action);
    }

    private static void run(Action action) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null) return;
        switch (action.verb) {
            case "giveaway" -> ShoreGames.open(ShoreGames.Kind.GIVEAWAY, action.code);
            case "tictactoe" -> ShoreGames.open(ShoreGames.Kind.TICTACTOE, "");
            case "clip" -> ShoreClient.recorder.saveManual();
        }
    }
}

package net.shoresmp.fabric;

import com.mojang.brigadier.arguments.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.MinecraftClient;
import net.shoresmp.core.*;
import java.util.*;

public final class ShoreCommands {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            dispatcher.register(ClientCommandManager.literal("shore").executes(ctx -> {
                ShoreClient.openNextTick(()->new ShoreScreen(null)); return 1;
            }).then(ClientCommandManager.literal("giveaway").executes(ctx -> ShoreGames.open(ShoreGames.Kind.GIVEAWAY,""))
                .then(ClientCommandManager.argument("code",StringArgumentType.word())
                    .executes(ctx -> ShoreGames.open(ShoreGames.Kind.GIVEAWAY, StringArgumentType.getString(ctx,"code")))))
              .then(ClientCommandManager.literal("tictactoe").executes(ctx -> ShoreGames.open(ShoreGames.Kind.TICTACTOE,"")))
              .then(ClientCommandManager.literal("clip").executes(ctx -> { ShoreClient.recorder.saveManual(); return 1; }))
              .then(ClientCommandManager.literal("status").executes(ctx->{ShoreClient.tell(ShoreClient.presenceStatus());ShoreClient.tell("Badges: "+ShoreClient.config.badges+", nametags: "+ShoreClient.config.badgeNametags+", tab: "+ShoreClient.config.badgeTab);ShoreClient.resyncPresence();return 1;})).then(ClientCommandManager.literal("deaths").executes(ctx -> deaths())
                .then(ClientCommandManager.literal("clear").executes(ctx -> {
                    ShoreClient.config.deaths.clear(); ShoreClient.config.save(); ShoreClient.tell("Death history cleared."); return 1;
                }))));
            dispatcher.register(ClientCommandManager.literal("paycancel").executes(ctx -> { ShoreClient.payments.cancel(true); return 1; }));
            dispatcher.register(ClientCommandManager.literal("paystatus").executes(ctx -> { ShoreClient.tell(ShoreClient.payments.status()); return 1; }));
            dispatcher.register(ClientCommandManager.literal("payall").then(ClientCommandManager.argument("amount", StringArgumentType.word())
                .executes(ctx -> plan("all", StringArgumentType.getString(ctx,"amount"), 0))));
            dispatcher.register(ClientCommandManager.literal("splitpay").then(ClientCommandManager.argument("total", StringArgumentType.word())
                .executes(ctx -> plan("split", StringArgumentType.getString(ctx,"total"), 0))));
            dispatcher.register(ClientCommandManager.literal("payrandom").then(ClientCommandManager.argument("amount", StringArgumentType.word())
                .executes(ctx -> plan("random", StringArgumentType.getString(ctx,"amount"), 1))
                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1,10000))
                    .executes(ctx -> plan("random", StringArgumentType.getString(ctx,"amount"), IntegerArgumentType.getInteger(ctx,"count"))))));
            dispatcher.register(ClientCommandManager.literal("payrain").then(ClientCommandManager.argument("total", StringArgumentType.word())
                .then(ClientCommandManager.argument("winners", IntegerArgumentType.integer(1,10000))
                    .executes(ctx -> plan("rain", StringArgumentType.getString(ctx,"total"), IntegerArgumentType.getInteger(ctx,"winners"))))));
            dispatcher.register(ClientCommandManager.literal("waypoint")
                .executes(ctx->{var screen=new ShoreScreen(null);screen.showModule("waypoints");ShoreClient.openNextTick(()->screen);return 1;})
                .then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("name",StringArgumentType.string())
                    .executes(ctx->waypoint(StringArgumentType.getString(ctx,"name"),""))
                    .then(ClientCommandManager.argument("x",IntegerArgumentType.integer(-30000000,30000000))
                    .then(ClientCommandManager.argument("y",IntegerArgumentType.integer(-2048,2048))
                    .then(ClientCommandManager.argument("z",IntegerArgumentType.integer(-30000000,30000000))
                    .executes(ctx->waypoint(StringArgumentType.getString(ctx,"name"),IntegerArgumentType.getInteger(ctx,"x")+" "+IntegerArgumentType.getInteger(ctx,"y")+" "+IntegerArgumentType.getInteger(ctx,"z"))))))))
                .then(ClientCommandManager.literal("list").executes(ctx->{var mc=MinecraftClient.getInstance();for(var marker:ShoreClient.config.savedWaypoints)if(marker.server.equals(ShoreVisuals.scope(mc)))ShoreClient.tell(marker.name+": "+marker.x+" "+marker.y+" "+marker.z+" ("+marker.dimension+")");return 1;})));
            // /modlist intentionally stays a server command. The plugin checks developer access.
        });
    }
    private static int waypoint(String name,String coordinates){
        var c=ShoreClient.config;String oldName=c.waypointName,oldCoordinates=c.waypointCoordinates;
        c.waypointName=name;c.waypointCoordinates=coordinates;boolean saved=ShoreVisuals.saveWaypoint();
        c.waypointName=oldName;c.waypointCoordinates=oldCoordinates;c.save();return saved?1:0;
    }
    private static int deaths() {
        var deaths = ShoreClient.config.deaths;
        if (deaths.isEmpty()) { ShoreClient.tell("No deaths recorded yet."); return 1; }
        ShoreClient.tell("Recent deaths (newest first):");
        for (int i = 0; i < deaths.size(); i++) ShoreClient.tell((i + 1) + ".  " + deaths.get(i));
        return 1;
    }
    private static int plan(String mode, String value, int count) {
        try {
            var client = MinecraftClient.getInstance();
            if (client.player == null || client.getNetworkHandler() == null || ShoreClient.lastRosterAt == 0)
                throw new IllegalArgumentException("The ShoreMod plugin has not supplied an online roster.");
            if (ShoreClient.payments.running()) throw new IllegalArgumentException("Use /paycancel before creating another payment queue.");
            var users = new ArrayList<>(ShoreClient.roster.users().values().stream()
                .filter(u -> !u.id().equals(client.player.getUuid()))
                .filter(u -> client.getNetworkHandler().getPlayerListEntry(u.id()) != null)
                .sorted(Comparator.comparing(Wire.User::name)).toList());
            var amount = Payments.amount(value);
            if (mode.equals("random") || mode.equals("rain")) {
                if (count > users.size()) throw new IllegalArgumentException("Only " + users.size() + " eligible players are in tab.");
                Collections.shuffle(users); users = new ArrayList<>(users.subList(0,count));
            }
            var payments = switch (mode) {
                case "split" -> Payments.split(users,amount,null);
                case "rain" -> Payments.split(users,amount,new Random());
                default -> Payments.each(users,amount);
            };
            var preview=new PaymentScreen(null, payments, client.getNetworkHandler());
            ShoreClient.openNextTick(()->preview);
            return 1;
        } catch (IllegalArgumentException e) { ShoreClient.tellError(e.getMessage()); return 0; }
    }
}

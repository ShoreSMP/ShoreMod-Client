package net.shoresmp.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.shoresmp.core.*;
import java.util.*;

public final class PaymentQueue {
    private final Deque<Payments.Payment> pending = new ArrayDeque<>();
    private ClientPlayNetworkHandler connection;
    private long nextSend;
    private long sessionEpoch;
    private int sent, skipped;
    public boolean running() { return !pending.isEmpty(); }
    public void start(List<Payments.Payment> plan) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (running()) throw new IllegalArgumentException("A payment queue is already active. Use /paycancel first.");
        if (client.getNetworkHandler() == null || ShoreClient.lastRosterAt == 0) throw new IllegalArgumentException("Connect to a server with the ShoreMod plugin first.");
        pending.addAll(plan); connection = client.getNetworkHandler(); sessionEpoch = ShoreClient.sessionEpoch;
        nextSend = 0; sent = skipped = 0;
        ShoreClient.tell("Payment queue started: $" + Payments.format(Payments.total(plan)) + " maximum. /paycancel stops remaining payments.");
    }
    public void tick(MinecraftClient client) {
        if (!running()) return;
        if (client.getNetworkHandler() != connection || sessionEpoch != ShoreClient.sessionEpoch
            || client.player == null || ShoreClient.lastRosterAt == 0) { cancel(true); return; }
        long now = System.nanoTime();
        if (now < nextSend) return;
        while (!pending.isEmpty()) {
            var payment = pending.removeFirst();
            var entry = connection.getPlayerListEntry(payment.user().id());
            if (entry == null || !ShoreClient.roster.users().containsKey(payment.user().id())
                || !entry.getProfile().name().equals(payment.user().name())) { skipped++; continue; }
            try {
                connection.sendChatCommand("pay " + payment.user().name() + " " + payment.amount().toPlainString());
            } catch(RuntimeException e) {
                ShoreClient.LOG.warn("Payment command send failed; cancelling without retry",e);
                cancel(false);
                ShoreClient.tellError("Payment queue stopped after a send error. Check your balance and server messages before starting again.");
                return;
            }
            sent++; nextSend = now + 2_000_000_000L;
            break;
        }
        if (pending.isEmpty()) ShoreClient.tell("Finished: " + sent + " /pay commands sent, " + skipped + " players skipped. Check the server's payment responses.");
    }
    public void cancel(boolean notify) {
        int count = pending.size(); pending.clear(); connection = null;
        if (notify) ShoreClient.tell("Cancelled " + count + " remaining payments. Already-sent payments cannot be undone.");
    }
    public String status() { return sent + " commands sent; " + pending.size() + " queued; " + skipped + " skipped."; }
}

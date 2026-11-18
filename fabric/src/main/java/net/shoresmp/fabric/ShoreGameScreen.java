package net.shoresmp.fabric;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

final class ShoreGameScreen extends Screen {
    private static final int WHITE = 0xFFF0F7FC, MUTED = 0xFFA5BAC9, ERROR = 0xFFFFA69D;
    private static final long SPIN_NANOS = 3_600_000_000L;
    private record Hit(float x, float y, float w, float h, Runnable click) {
        boolean contains(float mx, float my) { return mx >= x && mx < x+w && my >= y && my < y+h; }
    }
    private final ShoreGames.Kind kind;
    private final ShoreUi ui = new ShoreUi();
    private final List<Hit> hits = new ArrayList<>();
    private final ShoreScreen.Input code, reward, namedReward, duration, player, stake;
    private final String initialCode;
    private ShoreScreen.Input focused;
    private ShoreGames.Giveaway giveaway;
    private ShoreGames.Match match;
    private String errorText = "";
    private String requestedCode = "";
    private long requestedAt, spinStartedAt, nextRefreshAt;
    private boolean opened, home, unavailable, customReward, changingGiveaway;
    private float x, y, w, mx, my;
    private int accent, surface, edge;

    ShoreGameScreen(ShoreGames.Kind kind, String initialCode) {
        super(Text.literal(kind == ShoreGames.Kind.GIVEAWAY ? "Giveaway" : "Tic-tac-toe"));
        this.kind = kind;
        this.initialCode = initialCode;
        code = new ShoreScreen.Input("e.g. SHORE", initialCode, 24, null);
        reward = new ShoreScreen.Input("e.g. 5m", "", 32, null);
        namedReward = new ShoreScreen.Input("e.g. Turkey crate key", "", 80, null);
        duration = new ShoreScreen.Input("10 to 86400", "300", 5, null);
        player = new ShoreScreen.Input("Player name", "", 32, null);
        stake = new ShoreScreen.Input("0 for a friendly game", "0", 32, null);
    }

    @Override protected void init() {
        if (opened) return;
        opened = true;
        if (initialCode.isEmpty()) refresh(false);
        else join(initialCode);
    }

    private void refresh(boolean fresh) {
        var request = ShoreGames.request(kind == ShoreGames.Kind.GIVEAWAY ? "give.open" : "ttt.open");
        request.addProperty("fresh", fresh);
        changingGiveaway = fresh;
        if (!fresh && giveaway != null) request.addProperty("id", giveaway.id());
        submit(request);
    }

    private void retryOpen() {
        if (giveaway == null && !initialCode.isEmpty()) join(initialCode);
        else refresh(false);
    }

    private void submit(JsonObject request) {
        if (requestedAt != 0) return;
        errorText = "";
        focused = null;
        hits.clear();
        if (!ShoreGames.send(request)) {
            unavailable = true;
            error("Refresh after joining. This backend needs the updated ShoreMod plugin.");
            return;
        }
        unavailable = false;
        requestedAt = System.nanoTime();
    }

    void receive(JsonObject packet) {
        String op = ShoreGames.string(packet, "op", 16);
        if (op.equals("error")) {
            error(ShoreGames.string(packet, "message", 240));
            if (giveaway == null && match == null) home = true;
            return;
        }
        String prefix = kind == ShoreGames.Kind.GIVEAWAY ? "give." : "ttt.";
        if (!op.startsWith(prefix)) return;
        if (op.equals(prefix+"home")) {
            home = true; giveaway = null; match = null; spinStartedAt = 0;
        } else if (op.equals("give.state")) {
            var next = ShoreGames.giveaway(packet);
            if (!changingGiveaway && giveaway != null && !giveaway.id().equals(next.id())) return;
            if (!requestedCode.isEmpty() && !requestedCode.equalsIgnoreCase(next.code())) return;
            if (next.finished() && !next.winner().isEmpty() && (giveaway == null || !giveaway.finished()
                || !giveaway.code().equals(next.code()))) spinStartedAt = System.nanoTime();
            giveaway = next; home = false;
            nextRefreshAt = System.nanoTime()+4_000_000_000L;
        } else if (op.equals("ttt.state")) {
            match = ShoreGames.match(packet); home = false;
        } else return;
        hits.clear();
        requestedAt = 0;
        requestedCode = "";
        changingGiveaway = false;
        errorText = "";
        focused = null;
    }

    void error(String message) {
        errorText = message;
        requestedAt = 0;
        requestedCode = "";
        changingGiveaway = false;
        hits.clear();
    }

    @Override public void tick(){
        long now = System.nanoTime();
        if (unavailable && giveaway == null && match == null && (ShoreGames.available() || ShoreClient.hasCompanion())) retryOpen();
        if (requestedAt != 0 && now-requestedAt >= 7_000_000_000L)
            error("No game reply. Check this backend's ShoreMod plugin, then refresh.");
        if (giveaway != null && !giveaway.finished() && requestedAt == 0 && now >= nextRefreshAt) {
            nextRefreshAt = now+4_000_000_000L;
            var request = ShoreGames.request("give.open");
            request.addProperty("id", giveaway.id());
            ShoreGames.send(request);
        }
    }

    @Override public void render(DrawContext ctx,int guiX,int guiY,float delta){
        var window = client.getWindow();
        float unit = Math.min(ShoreUi.unit(client, ShoreClient.config.menuScale),
            Math.min(window.getFramebufferWidth()/420f, window.getFramebufferHeight()/358f));
        ui.begin(ctx, Math.max(0.1f, unit));
        hits.clear();
        mx = ui.fromGui(guiX); my = ui.fromGui(guiY);
        var config = ShoreClient.config;
        accent = 0xFF000000 | config.accent;
        int panel = config.panelColor();
        surface = 0xFF000000 | config.menuSurface;
        edge = 0xFF000000 | config.menuEdge;
        w = Math.min(438, ui.W-24);
        x = (ui.W-w)/2; y = (ui.H-334)/2;
        ShoreMenuBackground.draw(ui,config);
        ui.rect(0, 0, ui.W, ui.H, ShoreUi.alpha(0xFF000000, config.menuShade/100f));
        ui.outline(x, y, w, 334, 13, ShoreUi.mix(surface, accent, 0.30f), panel);
        ui.round(x+18, y+16, 34, 34, 8, surface);
        ui.icon("@logo", x+23, y+21, 24, WHITE);
        ui.text(getTitle().getString(), true, 17, x+64, y+23, WHITE, w-120);
        button("Close", x+w-73, y+20, 55, 26, this::close, false);
        ui.hline(x+18, y+59, w-36, edge);
        if (home) {
            if (kind == ShoreGames.Kind.GIVEAWAY) giveawayForm(); else matchForm();
        } else if (giveaway != null) giveawayState();
        else if (match != null) matchState();
        else ui.centered(unavailable ? "Games unavailable" : "Opening game", true, 16, x+w/2, y+148, WHITE, w-40);
        if (!errorText.isEmpty()) {
            ui.text(errorText, false, 11, x+18, y+295, ERROR, w-36);
            action("Refresh", x+w-83, y+309, 65, 19, this::retryOpen, false);
        } else if (requestedAt != 0) ui.text("Waiting for the server...", false, 11, x+18, y+306, MUTED);
        ui.end();
    }

    private void giveawayForm() {
        field("Giveaway code", code, x+20, y+71, w-137);
        action("Enter code", x+w-106, y+87, 85, 30, () -> join(code.text), false);
        ui.text("Prize type", false, 12, x+20, y+138, WHITE, 80);
        action("Money", x+106, y+130, (w-136)/2, 27, () -> {customReward=false; focused=null;}, !customReward);
        action("Custom", x+112+(w-136)/2, y+130, (w-136)/2, 27, () -> {customReward=true; focused=null;}, customReward);
        field(customReward ? "Prize name" : "Prize from your balance", customReward ? namedReward : reward, x+20, y+170, (w-54)/2);
        field("Duration in seconds", duration, x+34+(w-54)/2, y+170, (w-54)/2);
        ui.text(customReward ? "You deliver this prize to the winner." : "The server pays the winner from your prize fund.",
            false, 12, x+20, y+228, MUTED, w-40);
        action(customReward ? "Host giveaway" : reward.text.isBlank() ? "Fund giveaway" : "Fund giveaway with $"+reward.text,
            x+20, y+253, w-40, 33, this::host, true);
    }

    private void matchForm() {
        field("Challenge a ShoreMod player", player, x+20, y+79, w-40);
        field("Wager per player", stake, x+20, y+144, w-40);
        ui.text("Both players match the stake when the invite is accepted.", false, 12, x+20, y+210, MUTED, w-40);
        action("Send challenge", x+20, y+246, w-40, 36, this::invite, true);
    }

    private void join(String enteredCode) {
        String value = enteredCode.strip();
        if (!validCode(value)) { error("Use a 3-24 character code with letters, numbers, - or _."); home = true; return; }
        var request = ShoreGames.request("give.join");
        request.addProperty("code", value);
        requestedCode = value;
        submit(request);
    }
    private static boolean validCode(String value) { return value.matches("[A-Za-z0-9_-]{3,24}"); }

    private void host() {
        if (!validCode(code.text.strip())) { error("Use a 3-24 character giveaway code."); return; }
        String prize = (customReward ? namedReward : reward).text.strip();
        if (prize.isEmpty()) { error(customReward ? "Enter the prize you will deliver." : "Enter the in-game balance you want to give away."); return; }
        int seconds;
        try { seconds = Integer.parseInt(duration.text); } catch (NumberFormatException e) { seconds = 0; }
        if (seconds < 10 || seconds > 86400) { error("Choose a duration from 10 to 86400 seconds."); return; }
        var request = ShoreGames.request("give.create");
        request.addProperty("code", code.text.strip());
        request.addProperty("rewardType", customReward ? "custom" : "money");
        request.addProperty("reward", prize);
        request.addProperty("seconds", seconds);
        requestedCode = code.text.strip();
        submit(request);
    }

    private void invite() {
        if (player.text.isBlank() || stake.text.isBlank()) { error("Enter the player name and a wager (0 for a friendly game)."); return; }
        var request = ShoreGames.request("ttt.invite");
        request.addProperty("player", player.text.strip());
        request.addProperty("wager", stake.text.strip());
        submit(request);
    }

    private void giveawayState() {
        float cx = x+w*0.28f, cy = y+169;
        int count = giveaway.names().size();
        boolean finished = giveaway.finished();
        int index = finished ? giveaway.names().indexOf(giveaway.winner()) : -1;
        double progress = spinStartedAt == 0 ? 1 : Math.clamp((System.nanoTime()-spinStartedAt)/(double)SPIN_NANOS, 0, 1);
        boolean spinning = index >= 0 && progress < 1;
        double phase = index < 0 ? 0 : (3 + index/(double)count) * (1-Math.pow(1-progress, 3));
        boolean drewWinner = index >= 0 && !spinning;
        drawWheel(cx, cy, phase);
        int pointer = count == 0 ? 0 : Math.floorMod((int)Math.round(phase*count), count);
        String middle = index >= 0 ? giveaway.names().get(pointer) : giveaway.joined() && count>0
            ? String.format(Locale.ROOT, "%.1f%%", 100.0/count) : String.valueOf(count);
        ui.centered(middle, true, 15, cx, cy-13, drewWinner ? accent : WHITE, 103);
        String caption = index >= 0 ? (spinning ? "Drawing..." : "Winner") : giveaway.joined() ? "your chance" : "entries";
        ui.centered(caption, false, 10, cx, cy+7, MUTED, 95);
        float right = x+w*0.51f, space = x+w-20-right;
        ui.text((giveaway.custom() ? "" : "$")+giveaway.reward(), true, 18, right, y+109, accent, space);
        if (giveaway.custom()) ui.text("Host-delivered prize", false, 10, right, y+129, MUTED, space);
        ui.text("Hosted by "+giveaway.host(), false, 12, right, y+141, WHITE, space);
        ui.text("Code  "+giveaway.code(), false, 12, right, y+163, WHITE, space);
        ui.text(count+(count==1 ? " entry" : " entries"), false, 12, right, y+185, MUTED, space);
        ui.text(finished ? (spinning ? "Drawing winner" : "Draw complete") : "Closes in "+timeLeft(giveaway.ends()),
            false, 12, right, y+207, MUTED, space);
        if (finished)
            action("New giveaway", x+20, y+246, w-40, 36, () -> refresh(true), true);
        else
            button("Copy join command", x+20, y+246, w-40, 36,
                () -> client.keyboard.setClipboard("/shoremod giveaway "+giveaway.code()), false);
    }

    /** Rotate the same order the server drew from so the top marker lands on its chosen name. */
    private void drawWheel(float cx, float cy, double phase) {
        ui.circle(cx, cy, 133, ShoreUi.mix(surface, accent, 0.44f));
        ui.circle(cx, cy, 126, surface);
        int ticks = 36;
        for (int i = 0; i < ticks; i++) {
            double angle = (i/(double)ticks-phase)*2*Math.PI-Math.PI/2;
            float px = cx+(float)Math.cos(angle)*58, py = cy+(float)Math.sin(angle)*58;
            float size = i%3 == 0 ? 4 : 2;
            ui.rect(px-size/2, py-size/2, size, size, i%3 == 0 ? accent : MUTED);
        }
        ui.round(cx-3, cy-72, 6, 16, 2, WHITE);
    }

    private void matchState() {
        float left=x+21, top=y+89, cell=54, step=59;
        boolean playing = match.stage().equals("play"), myTurn = playing && match.you().equals(match.turn());
        for (int i=0; i<9; i++) {
            int number = i;
            char mark = match.board().charAt(i);
            float px=left+(i%3)*step, py=top+(i/3)*step;
            boolean enabled = myTurn && mark=='.' && requestedAt==0;
            var hit = new Hit(px, py, cell, cell, () -> move(number));
            int fill = enabled && hit.contains(mx, my) ? ShoreUi.mix(surface, accent, 0.28f) : surface;
            ui.outline(px, py, cell, cell, 7, edge, fill);
            if (mark != '.') ui.centered(String.valueOf(mark), true, 23, px+cell/2, py+15, mark=='X' ? accent : WHITE, cell-5);
            if (enabled) hits.add(hit);
        }
        float right=x+210, space=x+w-19-right;
        ui.text("X   "+match.x(), true, 14, right, y+105, accent, space);
        ui.text("O   "+match.o(), true, 14, right, y+129, WHITE, space);
        ui.text("Stake  $"+match.stake()+" each", false, 12, right, y+163, WHITE, space);
        if (!match.stage().equals("done"))
            ui.text("Time left  "+timeLeft(match.expires()), false, 12, right, y+184, MUTED, space);
        String detail = match.message().isBlank() && playing ? (myTurn ? "Your turn" : "Opponent's turn") : match.message();
        ui.text(detail, false, 12, left, y+270, WHITE, w-40);
        if (match.stage().equals("invite")) {
            if (match.you().equals("O")) {
                action("Match $"+match.stake(), right, y+209, space, 27, () -> matchAction("ttt.accept"), true);
                action("Decline", right, y+242, space, 23, () -> matchAction("ttt.decline"), false);
            } else action("Cancel invite", right, y+209, space, 28, () -> matchAction("ttt.decline"), false);
        } else if (!playing) action("New game", right, y+210, space, 28, () -> refresh(true), true);
    }

    private void matchAction(String op) {
        var request = ShoreGames.request(op);
        request.addProperty("id", match.id()); submit(request);
    }
    private void move(int cell) {
        var request = ShoreGames.request("ttt.move");
        request.addProperty("id", match.id()); request.addProperty("cell", cell); submit(request);
    }
    private static String timeLeft(long deadline) {
        long seconds = Math.max(0, (deadline-System.currentTimeMillis()+999)/1000);
        return seconds >= 3600 ? (seconds/3600)+"h "+(seconds%3600/60)+"m" : (seconds/60)+"m "+(seconds%60)+"s";
    }

    private void field(String label, ShoreScreen.Input input, float px, float py, float width) {
        ui.text(label, false, 12, px, py, WHITE, width);
        ui.outline(px, py+16, width, 30, 6, focused==input ? accent : ShoreUi.mix(surface, accent, 0.23f), surface);
        String visible = input.text.isEmpty() ? input.placeholder : input.text;
        ui.text(visible, false, 12, px+9, py+25, input.text.isEmpty() ? MUTED : WHITE, width-18);
        if (focused == input && System.currentTimeMillis()%1000<500) {
            float caret = Math.min(width-8, 9+ui.width(input.text.substring(0,input.cursor), false, 12));
            ui.rect(px+caret, py+23, 1, 15, accent);
        }
        if (requestedAt == 0) hits.add(new Hit(px, py+16, width, 30, () -> {focused=input; input.selectAll=true;}));
    }

    private void action(String label, float px, float py, float width, float height, Runnable click, boolean primary) {
        button(label, px, py, width, height, requestedAt==0 ? click : null, primary);
    }
    private void button(String label, float px, float py, float width, float height, Runnable click, boolean primary) {
        var hit = new Hit(px, py, width, height, click);
        int fill = primary ? accent : surface;
        if (click == null) fill = ShoreUi.mix(surface, fill, 0.3f);
        else {
            if (hit.contains(mx,my)) fill = ShoreUi.mix(fill, WHITE, 0.14f);
            hits.add(hit);
        }
        if (primary) ui.round(px, py, width, height, 6, fill);
        else ui.outline(px, py, width, height, 6, edge, fill);
        int brightness = (fill>>16&255)*299 + (fill>>8&255)*587 + (fill&255)*114;
        ui.centered(label, true, 12, px+width/2, py+(height-12)/2, brightness>145000 ? 0xFF08121E : WHITE, width-8);
    }

    @Override public boolean mouseClicked(Click click,boolean doubled){
        if (click.button()!=0) return false;
        for (Hit hit : hits) if (hit.contains(ui.fromGui(click.x()), ui.fromGui(click.y()))) {
            hit.click().run(); return true;
        }
        focused = null; return true;
    }
    @Override public boolean keyPressed(KeyInput input){
        if (focused == null) return super.keyPressed(input);
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (input.key()) {
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> focused = null;
            case GLFW.GLFW_KEY_TAB -> {
                List<ShoreScreen.Input> fields = kind == ShoreGames.Kind.GIVEAWAY ? List.of(code,customReward?namedReward:reward,duration) : List.of(player,stake);
                focused = fields.get((fields.indexOf(focused)+1)%fields.size()); focused.selectAll=true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> focused.backspace(ctrl);
            case GLFW.GLFW_KEY_DELETE -> focused.delete();
            case GLFW.GLFW_KEY_LEFT -> { focused.cursor=Math.max(0,focused.cursor-1); focused.selectAll=false; }
            case GLFW.GLFW_KEY_RIGHT -> { focused.cursor=Math.min(focused.text.length(),focused.cursor+1); focused.selectAll=false; }
            case GLFW.GLFW_KEY_A -> { if (ctrl) focused.selectAll=true; }
            case GLFW.GLFW_KEY_C -> { if (ctrl) client.keyboard.setClipboard(focused.text); }
            case GLFW.GLFW_KEY_V -> { if (ctrl) focused.insert(client.keyboard.getClipboard()); }
            default -> {}
        }
        return true;
    }
    @Override public boolean charTyped(CharInput input){
        if (focused == null || !input.isValidChar()) return false;
        focused.insert(input.asString()); return true;
    }
    @Override public boolean shouldPause() { return false; }
}

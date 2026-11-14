import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Generates ShoreMod's white UI icon set (96x96 RGBA, tinted at draw time) with Java2D.
 * Run from the project root:  java scripts/GenerateIcons.java
 * Every icon is original geometry drawn here, so the set carries no third-party license.
 */
public final class GenerateIcons {
    static final int S = 256;
    static final Path OUT = Path.of("fabric/src/main/resources/assets/shoremod/textures/gui/icon");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        // ---- chrome
        icon("grid", g -> { for (int x : new int[]{14, 52}) for (int y : new int[]{14, 52}) g.fill(rr(x, y, 30, 30, 9)); });
        icon("gear", g -> {
            Area a = new Area(new Ellipse2D.Double(20, 20, 56, 56));
            for (int i = 0; i < 8; i++) {
                AffineTransform t = AffineTransform.getRotateInstance(Math.toRadians(i * 45), 48, 48);
                a.add(new Area(t.createTransformedShape(new RoundRectangle2D.Double(40, 8, 16, 22, 5, 5))));
            }
            a.subtract(new Area(new Ellipse2D.Double(35, 35, 26, 26)));
            g.fill(a);
        });
        icon("heart", g -> { g.setStroke(stroke(8)); g.draw(heart()); });
        icon("heart_filled", g -> g.fill(heart()));
        icon("close", g -> { g.setStroke(stroke(10)); g.draw(line(26, 26, 70, 70)); g.draw(line(70, 26, 26, 70)); });
        icon("palette", g -> {
            Area a = new Area(new Ellipse2D.Double(10, 10, 76, 76));
            a.subtract(new Area(new Ellipse2D.Double(52, 54, 22, 22)));
            a.subtract(new Area(new Ellipse2D.Double(25, 30, 13, 13)));
            a.subtract(new Area(new Ellipse2D.Double(42, 20, 13, 13)));
            a.subtract(new Area(new Ellipse2D.Double(60, 30, 13, 13)));
            a.subtract(new Area(new Ellipse2D.Double(22, 50, 13, 13)));
            g.fill(a);
        });
        icon("search", g -> { g.setStroke(stroke(8)); g.draw(new Ellipse2D.Double(16, 16, 50, 50)); g.setStroke(stroke(10)); g.draw(line(62, 62, 82, 82)); });
        icon("download", g -> {
            g.setStroke(stroke(9)); g.draw(line(48, 14, 48, 58));
            g.draw(path(28, 40, 48, 60, 68, 40, false));
            g.draw(path(16, 66, 16, 80, 80, 80, false)); g.draw(line(80, 80, 80, 66));
        });
        icon("sliders", g -> {
            g.setStroke(stroke(7));
            int[] ys = {24, 48, 72}, ks = {30, 62, 42};
            for (int i = 0; i < 3; i++) { g.draw(line(14, ys[i], 82, ys[i])); g.fill(new Ellipse2D.Double(ks[i] - 10, ys[i] - 10, 20, 20)); }
        });
        icon("crosshair", g -> {
            g.setStroke(stroke(8)); g.draw(new Ellipse2D.Double(22, 22, 52, 52));
            g.setStroke(stroke(9)); g.draw(line(48, 6, 48, 30)); g.draw(line(48, 66, 48, 90)); g.draw(line(6, 48, 30, 48)); g.draw(line(66, 48, 90, 48));
            g.fill(new Ellipse2D.Double(42, 42, 12, 12));
        });
        icon("click", g -> {
            Path2D p = new Path2D.Double(); p.moveTo(40, 34); p.lineTo(40, 86); p.lineTo(52, 74); p.lineTo(61, 92); p.lineTo(70, 88); p.lineTo(61, 70); p.lineTo(78, 70); p.closePath();
            g.setStroke(stroke(5)); g.fill(p); g.draw(p);
            g.setStroke(stroke(7)); g.draw(line(40, 8, 40, 20)); g.draw(line(14, 34, 26, 34)); g.draw(line(20, 14, 28, 22)); g.draw(line(60, 14, 52, 22));
        });
        icon("sprint", g -> {
            g.setStroke(stroke(10)); g.draw(path(46, 22, 72, 48, 46, 74, false)); g.draw(path(22, 22, 48, 48, 22, 74, false));
            g.setStroke(stroke(7)); g.draw(line(64, 48, 88, 48));
        });
        icon("reach", g -> {
            g.setStroke(stroke(8)); g.draw(line(14, 48, 82, 48));
            g.draw(path(28, 34, 14, 48, 28, 62, false)); g.draw(path(68, 34, 82, 48, 68, 62, false));
            g.draw(line(14, 22, 14, 74)); g.draw(line(82, 22, 82, 74));
        });
        icon("combo", g -> {
            Path2D p = new Path2D.Double(); p.moveTo(56, 6); p.lineTo(20, 54); p.lineTo(44, 54); p.lineTo(36, 90); p.lineTo(76, 38); p.lineTo(52, 38); p.closePath();
            g.setStroke(stroke(4)); g.fill(p); g.draw(p);
        });
        icon("target", g -> {
            g.setStroke(stroke(8)); g.draw(new Ellipse2D.Double(12, 12, 72, 72)); g.draw(new Ellipse2D.Double(30, 30, 36, 36));
            g.fill(new Ellipse2D.Double(41, 41, 14, 14));
        });
        icon("potion", g -> {
            Area a = new Area(new Ellipse2D.Double(18, 34, 60, 56)); a.add(new Area(rr(36, 12, 24, 34, 6)));
            g.fill(a); g.setComposite(AlphaComposite.Clear); g.fill(rr(40, 16, 16, 8, 3)); g.setComposite(AlphaComposite.SrcOver);
            g.fill(rr(32, 6, 32, 10, 4));
        });
        icon("items", g -> {
            g.setStroke(stroke(7)); g.draw(rr(14, 30, 68, 56, 12)); g.draw(new Arc2D.Double(32, 10, 32, 36, 0, 180, Arc2D.OPEN));
            g.draw(line(14, 52, 82, 52)); g.fill(rr(42, 46, 12, 14, 3));
        });
        icon("totem", g -> {
            g.fill(rr(28, 6, 40, 34, 10)); g.fill(rr(20, 40, 56, 30, 8)); g.fill(rr(32, 70, 32, 20, 6));
            g.setComposite(AlphaComposite.Clear); g.fill(rr(36, 18, 8, 8, 3)); g.fill(rr(52, 18, 8, 8, 3)); g.setComposite(AlphaComposite.SrcOver);
        });
        icon("chevron_down", g -> { g.setStroke(stroke(11)); g.draw(path(20, 36, 48, 64, 76, 36, false)); });
        icon("chevron", g -> { g.setStroke(stroke(11)); g.draw(path(36, 20, 64, 48, 36, 76, false)); });
        icon("check", g -> { g.setStroke(stroke(11)); g.draw(path(20, 50, 40, 70, 78, 28, false)); });
        icon("back", g -> { g.setStroke(stroke(10)); g.draw(line(78, 48, 22, 48)); g.draw(path(44, 24, 20, 48, 44, 72, false)); });
        icon("moon", g -> { Area a = new Area(new Ellipse2D.Double(14, 14, 68, 68)); a.subtract(new Area(new Ellipse2D.Double(36, 4, 64, 64))); g.fill(a); });
        icon("sun", g -> {
            g.fill(new Ellipse2D.Double(30, 30, 36, 36)); g.setStroke(stroke(7));
            for (int i = 0; i < 8; i++) { double a = Math.toRadians(i * 45); g.draw(line(48 + Math.cos(a) * 30, 48 + Math.sin(a) * 30, 48 + Math.cos(a) * 42, 48 + Math.sin(a) * 42)); }
        });
        icon("folder", g -> {
            Area a = new Area(rr(8, 26, 80, 56, 12));
            a.add(new Area(rr(8, 16, 36, 24, 10)));
            Area hole = new Area(rr(16, 40, 64, 34, 6)); a.subtract(hole);
            g.fill(a);
        });
        icon("keyboard", g -> {
            g.setStroke(stroke(7)); g.draw(rr(8, 22, 80, 52, 12));
            for (int x = 22; x <= 70; x += 16) { g.fill(rr(x - 4, 34, 9, 9, 3)); }
            g.draw(line(30, 60, 66, 60));
        });
        // ---- modules
        icon("gauge", g -> {
            g.setStroke(stroke(9)); g.draw(new Arc2D.Double(12, 16, 72, 72, -30, 240, Arc2D.OPEN));
            g.setStroke(stroke(9)); g.draw(line(48, 52, 66, 32)); g.fill(new Ellipse2D.Double(40, 44, 16, 16));
        });
        icon("signal", g -> { int[] h = {18, 34, 50, 66}; for (int i = 0; i < 4; i++) g.fill(rr(12 + i * 19, 82 - h[i], 14, h[i], 6)); });
        icon("pin", g -> {
            Area a = new Area(new Ellipse2D.Double(20, 8, 56, 56));
            a.add(new Area(path(24, 46, 48, 90, 72, 46, true)));
            a.subtract(new Area(new Ellipse2D.Double(36, 24, 24, 24)));
            g.fill(a);
        });
        icon("tree", g -> {
            g.fill(path(48, 6, 76, 44, 20, 44, true)); g.fill(path(48, 24, 84, 70, 12, 70, true));
            g.fill(rr(41, 66, 14, 22, 3));
        });
        icon("clock", g -> { g.setStroke(stroke(8)); g.draw(new Ellipse2D.Double(12, 12, 72, 72)); g.draw(path(48, 26, 48, 48, 64, 58, false)); });
        icon("armor", g -> {
            Path2D p = new Path2D.Double();
            p.moveTo(28, 12); p.lineTo(40, 12); p.lineTo(48, 22); p.lineTo(56, 12); p.lineTo(68, 12); p.lineTo(86, 30);
            p.lineTo(76, 44); p.lineTo(70, 40); p.lineTo(70, 84); p.lineTo(26, 84); p.lineTo(26, 40); p.lineTo(20, 44); p.lineTo(10, 30); p.closePath();
            g.setStroke(stroke(4)); g.fill(p); g.draw(p);
        });
        icon("stack", g -> {
            g.setStroke(stroke(7)); g.draw(rr(30, 10, 56, 38, 12));
            Area front = new Area(rr(10, 34, 58, 40, 12)); front.add(new Area(path(20, 66, 18, 88, 40, 70, true)));
            g.setComposite(AlphaComposite.Clear); g.setStroke(stroke(10)); g.draw(front); g.setComposite(AlphaComposite.SrcOver);
            g.fill(front);
        });
        icon("chat", g -> {
            Area a = new Area(rr(8, 12, 80, 56, 16)); a.add(new Area(path(22, 60, 18, 88, 46, 66, true)));
            Area inner = new Area(rr(16, 20, 64, 40, 10)); a.subtract(inner);
            g.fill(a); g.setStroke(stroke(6)); g.draw(line(28, 34, 68, 34)); g.draw(line(28, 47, 56, 47));
        });
        icon("history", g -> {
            g.setStroke(stroke(8)); g.draw(new Arc2D.Double(14, 14, 68, 68, 150, -300, Arc2D.OPEN));
            g.fill(path(6, 30, 30, 32, 16, 52, true));
            g.draw(path(48, 30, 48, 50, 62, 58, false));
        });
        icon("bell", g -> {
            Path2D p = new Path2D.Double();
            p.moveTo(48, 10); p.curveTo(30, 10, 24, 26, 24, 42); p.lineTo(24, 60); p.lineTo(14, 72); p.lineTo(82, 72); p.lineTo(72, 60);
            p.lineTo(72, 42); p.curveTo(72, 26, 66, 10, 48, 10); p.closePath();
            g.fill(p); g.fill(new Ellipse2D.Double(38, 72, 20, 16));
        });
        icon("mouse", g -> {
            g.setStroke(stroke(7)); g.draw(rr(24, 8, 48, 80, 46)); g.draw(line(48, 8, 48, 38)); g.draw(line(24, 38, 72, 38));
        });
        icon("box", g -> {
            g.setStroke(stroke(7));
            g.draw(path4(48, 12, 82, 30, 48, 48, 14, 30));
            g.draw(path(14, 30, 14, 66, 48, 84, false)); g.draw(path(48, 84, 82, 66, 82, 30, false)); g.draw(line(48, 48, 48, 84));
        });
        icon("eye_off", g -> {
            Path2D eye = new Path2D.Double(); eye.moveTo(8, 48); eye.quadTo(48, 4, 88, 48); eye.quadTo(48, 92, 8, 48); eye.closePath();
            g.setStroke(stroke(7)); g.draw(eye); g.fill(new Ellipse2D.Double(36, 36, 24, 24));
            g.setComposite(AlphaComposite.Clear); g.setStroke(stroke(18)); g.draw(line(18, 82, 80, 14));
            g.setComposite(AlphaComposite.SrcOver); g.setStroke(stroke(8)); g.draw(line(18, 82, 80, 14));
        });
        icon("cube", g -> {
            g.setColor(new Color(255, 255, 255, 255)); g.fill(path4(48, 10, 84, 29, 48, 48, 12, 29));
            g.setColor(new Color(255, 255, 255, 170)); g.fill(path4(12, 33, 46, 52, 46, 88, 12, 69));
            g.setColor(new Color(255, 255, 255, 105)); g.fill(path4(50, 52, 84, 33, 84, 69, 50, 88));
        });
        icon("zoom", g -> {
            g.setStroke(stroke(8)); g.draw(new Ellipse2D.Double(12, 12, 54, 54)); g.setStroke(stroke(11)); g.draw(line(62, 62, 84, 84));
            g.setStroke(stroke(7)); g.draw(line(39, 28, 39, 50)); g.draw(line(28, 39, 50, 39));
        });
        icon("pickaxe", g -> {
            g.rotate(Math.toRadians(45), 48, 48);
            Path2D head = new Path2D.Double(); head.moveTo(10, 36); head.quadTo(48, 6, 86, 36); head.quadTo(48, 22, 10, 36); head.closePath();
            g.setStroke(stroke(7)); g.fill(head); g.draw(head);
            g.setStroke(stroke(10)); g.draw(line(48, 26, 48, 90));
        });
        icon("skull", g -> {
            Area a = new Area(new Ellipse2D.Double(16, 8, 64, 60)); a.add(new Area(rr(28, 50, 40, 36, 10)));
            a.subtract(new Area(new Ellipse2D.Double(28, 32, 17, 17))); a.subtract(new Area(new Ellipse2D.Double(51, 32, 17, 17)));
            a.subtract(new Area(path(48, 52, 42, 62, 54, 62, true)));
            a.subtract(new Area(new Rectangle2D.Double(39, 70, 4, 16))); a.subtract(new Area(new Rectangle2D.Double(53, 70, 4, 16)));
            g.fill(a);
        });
        icon("reconnect", g -> {
            g.setStroke(stroke(8));
            g.draw(new Arc2D.Double(16, 16, 64, 64, 30, 130, Arc2D.OPEN));
            g.draw(new Arc2D.Double(16, 16, 64, 64, 210, 130, Arc2D.OPEN));
            arrowHead(g, 160, true); arrowHead(g, 340, true);
        });
        icon("record", g -> { g.fill(rr(8, 24, 58, 48, 12)); g.fill(path(70, 48, 88, 30, 88, 66, true)); });
        icon("shield", g -> {
            Path2D p = new Path2D.Double(); p.moveTo(48, 8); p.lineTo(82, 20); p.curveTo(82, 56, 70, 76, 48, 88); p.curveTo(26, 76, 14, 56, 14, 20); p.closePath();
            g.fill(p);
        });
        System.out.println("Icons written to " + OUT.toAbsolutePath());
    }

    static void arrowHead(Graphics2D g, double deg, boolean ccw) {
        double a = Math.toRadians(deg), x = 48 + Math.cos(a) * 32, y = 48 - Math.sin(a) * 32;
        // tangent for counter-clockwise travel
        double tx = -Math.sin(a), ty = -Math.cos(a);
        double nx = Math.cos(a), ny = -Math.sin(a);
        Path2D p = new Path2D.Double();
        p.moveTo(x + tx * 14, y + ty * 14);
        p.lineTo(x + nx * 11 - tx * 2, y + ny * 11 - ty * 2);
        p.lineTo(x - nx * 11 - tx * 2, y - ny * 11 - ty * 2);
        p.closePath(); g.fill(p);
    }
    static Shape heart() {
        Path2D p = new Path2D.Double();
        p.moveTo(48, 82); p.curveTo(14, 60, 8, 40, 14, 28); p.curveTo(22, 12, 42, 12, 48, 28);
        p.curveTo(54, 12, 74, 12, 82, 28); p.curveTo(88, 40, 82, 60, 48, 82); p.closePath();
        return p;
    }
    static Shape rr(double x, double y, double w, double h, double r) { return new RoundRectangle2D.Double(x, y, w, h, r, r); }
    static Shape line(double a, double b, double c, double d) { return new Line2D.Double(a, b, c, d); }
    static Path2D path(double x1, double y1, double x2, double y2, double x3, double y3, boolean close) {
        Path2D p = new Path2D.Double(); p.moveTo(x1, y1); p.lineTo(x2, y2); p.lineTo(x3, y3); if (close) p.closePath(); return p;
    }
    static Path2D path4(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
        Path2D p = path(x1, y1, x2, y2, x3, y3, false); p.lineTo(x4, y4); p.closePath(); return p;
    }
    static BasicStroke stroke(float w) { return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); }
    static void icon(String name, Consumer<Graphics2D> draw) throws Exception {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(S / 96.0, S / 96.0);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        draw.accept(g);
        g.dispose();
        ImageIO.write(img, "png", OUT.resolve(name + ".png").toFile());
    }
}

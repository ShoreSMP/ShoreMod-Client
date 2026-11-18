package net.shoresmp.fabric;

import net.minecraft.client.MinecraftClient;
import java.util.List;

/** Development-only integration checks, invoked by the compatibility smoke-test property. */
public final class ShoreSettingsChecks {
    private ShoreSettingsChecks(){}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    private static ShoreScreen.Row row(ShoreScreen screen,String title)throws ReflectiveOperationException{
        var field=ShoreScreen.class.getDeclaredField("rows");field.setAccessible(true);
        return ((List<ShoreScreen.Row>)field.get(screen)).stream().filter(r->r.title.equals(title)).findFirst().orElseThrow();
    }
    private static void confirm(ShoreScreen screen,ShoreScreen.Input input,String value)throws ReflectiveOperationException{
        input.set(value,true);
        var focus=ShoreScreen.class.getDeclaredField("focused");focus.setAccessible(true);focus.set(screen,input);
        var commit=ShoreScreen.class.getDeclaredMethod("finishInput",boolean.class);commit.setAccessible(true);commit.invoke(screen,true);
    }
    public static void run(){
        Config c=ShoreClient.config;
        boolean enabled=c.fullbright;int strength=c.fullbrightStrength,size=c.hudSize("fps"),color=c.crosshairCustomColor,mode=c.crosshairColor;
        try{
            Config legacy = new com.google.gson.Gson().fromJson("""
                {"menuDefaults140":true,"menuPalette150":true,"panelOpacity":100,"menuBackgroundColor":597030,
                 "menuSurface":1058353,"hudBackgroundColor":1053207,"hudBgOpacity":85,
                 "watchBackground":1058353,"watchOpacity":85}
                """,Config.class);
            legacy.applyBlackBackgroundDefaults();
            check(legacy.panelColor()==0xFF000000 && legacy.menuSurface==0
                && legacy.hudBackgroundColor==0 && legacy.hudBgOpacity==100
                && legacy.watchBackground==0 && legacy.watchOpacity==100,
                "previous default menu and HUD fills migrate to opaque black");
            legacy.menuSurface=0x102631;
            legacy.applyBlackBackgroundDefaults();
            check(legacy.menuSurface==0x102631,"reselecting the former color after migration is respected");
            Config custom=new Config();
            custom.menuBackgroundColor=0x334455;custom.panelOpacity=38;custom.hudBackgroundColor=0xAABBCC;
            custom.hudBgOpacity=85;custom.watchBackground=0x102631;custom.watchOpacity=44;
            custom.applyBlackBackgroundDefaults();
            check(custom.panelColor()==0x61334455 && custom.hudBackgroundColor==0xAABBCC
                && custom.hudBgOpacity==85 && custom.watchOpacity==44,"custom colors and opacity survive migration");
            c.fullbright=false;double vanilla=MinecraftClient.getInstance().options.getGamma().getValue();
            var screen=new ShoreScreen(null);screen.showModule("fullbright");
            var numeric=row(screen,"Brightness boost").input;
            confirm(screen,numeric,"37");check(c.fullbrightStrength==37,"custom brightness is applied");
            numeric.set("101",true);check(c.fullbrightStrength==37&&!numeric.validation.apply(numeric.text).isEmpty(),"over-limit input is rejected");
            numeric.set("NaN",true);check(c.fullbrightStrength==37,"non-numeric input is rejected");
            c.fullbright=true;
            double bright=MinecraftClient.getInstance().options.getGamma().getValue();
            check(bright>vanilla&&bright<=16,"fullbright reaches the real game gamma getter");
            MinecraftClient.getInstance().options.write();
            String saved=java.nio.file.Files.readString(MinecraftClient.getInstance().runDirectory.toPath().resolve("options.txt"));
            double stored=saved.lines().filter(line->line.startsWith("gamma:")).mapToDouble(line->Double.parseDouble(line.substring(6))).findFirst().orElseThrow();
            check(stored==vanilla,"saving game options cannot persist boosted gamma");
            check(MinecraftClient.getInstance().options.getGamma().getValue()==bright,"fullbright resumes after options save");
            c.fullbright=false;
            check(MinecraftClient.getInstance().options.getGamma().getValue()==vanilla,"fullbright restores the unchanged vanilla value");
            screen.showModule("fps");confirm(screen,row(screen,"Element size").input,"173");
            check(c.hudSize("fps")==173,"typed values do not snap to slider presets");
            screen.showModule("crosshair");var rgb=row(screen,"Custom crosshair color").input;
            rgb.set("#12AB34",true);check(c.crosshairCustomColor==0x12AB34&&c.crosshairColor==6,"custom hex color is applied");
            rgb.set("#GGGGGG",true);check(c.crosshairCustomColor==0x12AB34,"invalid hex cannot overwrite a color");
            int[] painted=c.crosshairPaint.clone();String image=c.crosshairImage;int imageMode=c.crosshairMode,imageSize=c.crosshairImageSize;
            boolean crosshair=c.crosshair,tint=c.crosshairTint;
            var file=ShoreHud.CrosshairImage.folder().resolve("painted-crosshair.png");byte[] previous=java.nio.file.Files.exists(file)?java.nio.file.Files.readAllBytes(file):null;
            try{
                var editor=new CrosshairEditorScreen(screen);
                var field=CrosshairEditorScreen.class.getDeclaredField("canvas");field.setAccessible(true);
                var canvas=(net.shoresmp.core.PixelCanvas)field.get(editor);canvas.begin();canvas.line(15,15,16,16,0xFF50A7FF);canvas.end();
                var save=CrosshairEditorScreen.class.getDeclaredMethod("save");save.setAccessible(true);save.invoke(editor);
                check(c.crosshair&&c.crosshairMode==1&&!c.crosshairTint,"painted crosshair becomes active without image tint");
                check(javax.imageio.ImageIO.read(file.toFile()).getRGB(15,15)==0xFF50A7FF,"painted color survives PNG save");
                check(ShoreHud.CrosshairImage.texture(c.crosshairImage)!=null,"painted PNG loads into the real texture manager");
            }finally{
                c.crosshairPaint=painted;c.crosshairImage=image;c.crosshairMode=imageMode;c.crosshairImageSize=imageSize;c.crosshair=crosshair;c.crosshairTint=tint;
                if(previous==null)java.nio.file.Files.deleteIfExists(file);else java.nio.file.Files.write(file,previous);
                ShoreHud.CrosshairImage.invalidate();
            }
            int watchStyle=c.watchStyle;
            boolean autoWidth=c.watchAutoWidth;
            try {
                screen.showModule("stopwatch");
                row(screen,"Display mode").iset.accept(0);
                row(screen,"Background width").iset.accept(1);
                check(row(screen,"Fixed width").available.getAsBoolean(),"fixed stopwatch width is editable");
                row(screen,"Display mode").iset.accept(2);
                check(!row(screen,"Fixed width").available.getAsBoolean(),"text-only stopwatch hides box controls");
                screen.showPage("GENERAL");
                check(row(screen,"Choose a background").action!=null,"background picker is wired");
                check(row(screen,"Image fit").options.length==3,"background supports cover, fit and stretch");
            } finally { c.watchStyle=watchStyle;c.watchAutoWidth=autoWidth; }
            ShoreClient.LOG.info("SHORE_CUSTOM_SETTINGS_PASS");
        }catch(ReflectiveOperationException|java.io.IOException e){throw new IllegalStateException(e);}
        finally{c.fullbright=enabled;c.fullbrightStrength=strength;c.hudSizes.put("fps",size);c.crosshairCustomColor=color;c.crosshairColor=mode;c.save();}
    }
}

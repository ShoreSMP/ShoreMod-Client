package net.shoresmp.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class ClipSavedScreen extends ShoreDialog {
    private final Path clip;
    private final double seconds;
    public ClipSavedScreen(Screen parent,Path clip,double seconds){super("Gameplay clip saved",parent);this.clip=clip;this.seconds=seconds;}
    @Override protected String icon(){return "record";}
    @Override protected String subtitle(){return String.format(Locale.ROOT,"%.1f seconds saved to this computer",seconds);}
    @Override protected List<Line> lines(){
        return List.of(new Line("Your recording is ready.",TEXT,true),
                       new Line("Use Show clip to open its folder.",MUTED,false));
    }
    @Override protected List<Button> buttons(){
        return List.of(new Button("Close",SECONDARY,this::close),
                       new Button("Show clip",PRIMARY,()->Util.getOperatingSystem().open(clip.getParent())));
    }
}

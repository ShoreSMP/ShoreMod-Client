package net.shoresmp.fabric;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import java.util.*;

/** A centered ShoreMod-styled dialog drawn at native resolution: icon, title, body lines and buttons. */
abstract class ShoreDialog extends Screen {
    protected record Line(String text,int color,boolean bold){}
    protected record Button(String label,int style,Runnable action){}
    protected static final int SECONDARY=0,PRIMARY=1,DANGER=2;
    protected static final int TEXT=0xFFF4F5F7,MUTED=0xFF9BA1AA;
    private record Hit(float x,float y,float w,float h,Runnable action){}
    private final ShoreUi ui=new ShoreUi();
    private final List<Hit> hits=new ArrayList<>();
    protected final Screen parent;

    protected ShoreDialog(String title,Screen parent){super(Text.literal(title));this.parent=parent;}
    protected abstract String icon();
    protected abstract String subtitle();
    protected abstract List<Line> lines();
    protected abstract List<Button> buttons();
    protected float dialogScale(){return 1;}
    protected float preferredWidth(){return 480;}
    protected float buttonMinimum(){return 96;}

    @Override public void render(DrawContext ctx,int gx,int gy,float delta){
        ui.begin(ctx,ShoreUi.unit(client,ShoreClient.config.menuScale)*dialogScale());
        ui.sans=false;ui.noBold=true; // Minecraft's pixel font, regular weight
        float mx=ui.fromGui(client.mouse.getScaledX(client.getWindow())),my=ui.fromGui(client.mouse.getScaledY(client.getWindow()));
        hits.clear();
        int accent=0xFF000000|ShoreClient.config.accent;
        ui.rect(0,0,ui.W,ui.H,0x66000000);
        List<Line> body=lines();List<Button> btns=buttons();
        float w=Math.min(preferredWidth(),ui.W-40),pad=24,h=pad+44+12+body.size()*22+16+38+pad;
        float x=(ui.W-w)/2,y=(ui.H-h)/2;
        ui.outline(x,y,w,h,14,0xFF23272D,0xFA101215);
        ui.round(x+pad,y+pad,40,40,10,ShoreUi.alpha(accent,0.16f));
        ui.icon(icon(),x+pad+10,y+pad+10,20,accent);
        ui.text(getTitle().getString(),true,17,x+pad+54,y+pad+2,TEXT,w-pad*2-54);
        ui.text(subtitle(),false,13,x+pad+54,y+pad+24,MUTED,w-pad*2-54);
        float ty=y+pad+44+16;
        for(Line l:body){ui.text(l.text(),l.bold(),13.5f,x+pad,ty,l.color(),w-pad*2);ty+=22;}
        float bx=x+w-pad,by=y+h-pad-38;
        for(int i=btns.size()-1;i>=0;i--){
            Button b=btns.get(i);
            float bw=Math.max(buttonMinimum(),ui.width(b.label(),true,13.5f)+28);bx-=bw;
            boolean hov=mx>=bx&&my>=by&&mx<bx+bw&&my<by+38;
            hits.add(new Hit(bx,by,bw,38,b.action()));
            int bg=switch(b.style()){
                case PRIMARY->hov?ShoreUi.mix(accent,0xFFFFFFFF,0.12f):accent;
                case DANGER->hov?0xFFF0585D:0xFFE5484D;
                default->hov?0xFF2A2F35:0xFF1E2227;
            };
            int fg=b.style()==PRIMARY?(light(accent)?0xFF0D0F12:0xFFFFFFFF):b.style()==DANGER?0xFFFFFFFF:TEXT;
            ui.round(bx,by,bw,38,9,bg);
            ui.centered(b.label(),true,13.5f,bx+bw/2,by+(38-13.5f)/2,fg,bw-12);
            bx-=10;
        }
        ui.end();
    }
    private static boolean light(int c){return ((c>>16&255)*299+(c>>8&255)*587+(c&255)*114)/1000>150;}
    @Override public boolean mouseClicked(Click click,boolean doubled){
        float px=ui.fromGui(click.x()),py=ui.fromGui(click.y());
        for(Hit h:hits)if(px>=h.x()&&py>=h.y()&&px<h.x()+h.w()&&py<h.y()+h.h()){
            client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK,1f));
            h.action().run();return true;
        }
        return true;
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}

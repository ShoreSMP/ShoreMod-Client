package net.shoresmp.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import java.util.*;

/**
 * Native-resolution painter for ShoreMod's UI. Everything is laid out in "logical" units that are
 * independent of Minecraft's GUI scale (1 unit = {@link #u} framebuffer pixels) and drawn in real
 * pixels, so edges land on the pixel grid instead of being blown up by the GUI scale.
 *  - Rounded rectangles and circles use anti-aliased corner textures generated for the exact pixel radius.
 *  - Icons are box-filtered at runtime down to the exact pixel size they are drawn at.
 *  - Text uses Minecraft's pixel font at a whole-number pixel scale, so it is always sharp.
 */
final class ShoreUi {
    private static final Map<Integer,Identifier> CORNERS=new HashMap<>();
    private static final Map<String,Identifier> ICONS=new HashMap<>();
    private static final Map<String,NativeImage> ICON_SOURCES=new HashMap<>();
    private static final Map<String,Text> STYLED=new LinkedHashMap<>(256,0.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,Text> entry){return size()>2048;}};

    final MinecraftClient mc=MinecraftClient.getInstance();
    DrawContext ctx;
    float u;      // framebuffer pixels per logical unit
    int gs;       // Minecraft GUI scale (framebuffer pixels per GUI unit)
    float W,H;    // logical canvas size

    /** Logical unit size for the current window: a ~560-unit-tall canvas at any resolution. */
    static float unit(MinecraftClient mc,int menuScale){
        int fbH=mc.getWindow().getFramebufferHeight();
        return Math.clamp(fbH/500f,1f,4.5f)*menuScale/100f;
    }
    void begin(DrawContext ctx,float unit){
        fontScale=ShoreClient.config==null?1:ShoreClient.config.menuFontScale/100f;
        this.ctx=ctx;this.u=unit;
        var w=mc.getWindow();gs=w.getScaleFactor();
        W=w.getFramebufferWidth()/u;H=w.getFramebufferHeight()/u;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(1f/gs,1f/gs); // now 1 unit = 1 framebuffer pixel
    }
    void end(){ctx.getMatrices().popMatrix();}
    int p(float v){return Math.round(v*u);}
    /** GUI-space coordinate (as Minecraft reports the mouse) to logical units. */
    float fromGui(double gui){return (float)(gui*gs/u);}

    // ------------------------------------------------------------------ shapes
    void rect(float x,float y,float w,float h,int color){
        int x0=p(x),y0=p(y),x1=p(x+w),y1=p(y+h);
        if(x1>x0&&y1>y0)ctx.fill(x0,y0,x1,y1,color);
    }
    void hline(float x,float y,float w,int color){int x0=p(x),y0=p(y);ctx.fill(x0,y0,p(x+w),y0+1,color);}
    void round(float x,float y,float w,float h,float r,int color){roundPx(p(x),p(y),p(x+w),p(y+h),p(r),color);}
    /** Rounded rect with a 1-pixel border. The fill should be opaque or at least as opaque as the border. */
    void outline(float x,float y,float w,float h,float r,int border,int fill){
        int x0=p(x),y0=p(y),x1=p(x+w),y1=p(y+h),rr=p(r);
        roundPx(x0,y0,x1,y1,rr,border);
        roundPx(x0+1,y0+1,x1-1,y1-1,Math.max(0,rr-1),fill);
    }
    /** Ring of the given pixel thickness (for outlined buttons), drawn as border + inner cut-in fill. */
    void ring(float x,float y,float w,float h,float r,int color,int inner){
        int x0=p(x),y0=p(y),x1=p(x+w),y1=p(y+h),rr=p(r),t=Math.max(1,Math.round(u*0.9f));
        roundPx(x0,y0,x1,y1,rr,color);
        roundPx(x0+t,y0+t,x1-t,y1-t,Math.max(0,rr-t),inner);
    }
    void circle(float cx,float cy,float d,int color){round(cx-d/2,cy-d/2,d,d,d/2,color);}
    void roundPx(int x0,int y0,int x1,int y1,int r,int color){
        int w=x1-x0,h=y1-y0;
        if(w<=0||h<=0||(color>>>24)==0)return;
        r=Math.min(r,Math.min(w,h)/2);
        if(r<=0){ctx.fill(x0,y0,x1,y1,color);return;}
        Identifier tex=corner(r);int d=r*2;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,tex,x0,y0,0f,0f,r,r,d,d,color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,tex,x1-r,y0,r,0f,r,r,d,d,color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,tex,x0,y1-r,0f,r,r,r,d,d,color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,tex,x1-r,y1-r,r,r,r,r,d,d,color);
        if(x1-r>x0+r)ctx.fill(x0+r,y0,x1-r,y1,color);
        if(y1-r>y0+r){ctx.fill(x0,y0+r,x0+r,y1-r,color);ctx.fill(x1-r,y0+r,x1,y1-r,color);}
    }
    private Identifier corner(int r){
        return CORNERS.computeIfAbsent(r,radius->{
            int d=radius*2,ss=4;NativeImage img=new NativeImage(d,d,false);
            for(int y=0;y<d;y++)for(int x=0;x<d;x++){
                int inside=0;
                for(int sy=0;sy<ss;sy++)for(int sx=0;sx<ss;sx++){
                    double px=x+(sx+0.5)/ss-radius,py=y+(sy+0.5)/ss-radius;
                    if(px*px+py*py<=radius*(double)radius)inside++;
                }
                int a=Math.round(inside*255f/(ss*ss));
                img.setColorArgb(x,y,(a<<24)|0xFFFFFF);
            }
            Identifier id=Identifier.of("shoremod","dyn/corner_"+radius);
            mc.getTextureManager().registerTexture(id,new NativeImageBackedTexture(()->"shoremod corner "+radius,img));
            return id;
        });
    }

    // ------------------------------------------------------------------ icons
    /** Draws a white icon tinted with {@code color}; "@logo" draws the full-color Shore badge. */
    void icon(String name,float x,float y,float size,int color){
        int s=Math.max(1,p(size));
        Identifier id=iconTexture(name,s);
        if(id==null)return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,id,p(x),p(y),0f,0f,s,s,s,s,name.equals("@logo")?0xFFFFFFFF:color);
    }
    private Identifier iconTexture(String name,int s){
        String key=name+"@"+s;
        Identifier cached=ICONS.get(key);
        if(cached!=null)return cached;
        NativeImage src=ICON_SOURCES.computeIfAbsent(name,n->{
            Identifier file=n.equals("@logo")?Identifier.of("shoremod","textures/font/badge.png"):Identifier.of("shoremod","textures/gui/icon/"+n+".png");
            try(var in=mc.getResourceManager().getResource(file).orElseThrow().getInputStream()){return NativeImage.read(in);}
            catch(Exception e){ShoreClient.LOG.warn("Missing ShoreMod icon {}",n);return null;}
        });
        if(src==null)return null;
        NativeImage out=downscale(src,s);
        Identifier id=Identifier.of("shoremod","dyn/icon/"+name.replace("@","")+"_"+s);
        mc.getTextureManager().registerTexture(id,new NativeImageBackedTexture(()->"shoremod icon "+key,out));
        ICONS.put(key,id);
        return id;
    }
    /** Premultiplied box filter: every source pixel contributes by the area it covers. */
    private static NativeImage downscale(NativeImage src,int s){
        int sw=src.getWidth(),sh=src.getHeight();
        NativeImage out=new NativeImage(s,s,false);
        double fx=sw/(double)s,fy=sh/(double)s;
        for(int dy=0;dy<s;dy++){
            double y0=dy*fy,y1=(dy+1)*fy;
            for(int dx=0;dx<s;dx++){
                double x0=dx*fx,x1=(dx+1)*fx,a=0,r=0,g=0,b=0,area=0;
                for(int sy=(int)y0;sy<Math.min(sh,(int)Math.ceil(y1));sy++){
                    double wy=Math.min(y1,sy+1)-Math.max(y0,sy);
                    for(int sx=(int)x0;sx<Math.min(sw,(int)Math.ceil(x1));sx++){
                        double wx=Math.min(x1,sx+1)-Math.max(x0,sx),w=wx*wy;
                        int c=src.getColorArgb(sx,sy);double ca=(c>>>24)/255.0;
                        a+=ca*w;r+=(c>>16&255)*ca*w;g+=(c>>8&255)*ca*w;b+=(c&255)*ca*w;area+=w;
                    }
                }
                int oa=(int)Math.round(a/area*255),or=0,og=0,ob=0;
                if(a>0){or=(int)Math.round(r/a);og=(int)Math.round(g/a);ob=(int)Math.round(b/a);}
                out.setColorArgb(dx,dy,(oa<<24)|(Math.min(255,or)<<16)|(Math.min(255,og)<<8)|Math.min(255,ob));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ text
    private static final float[] OVERSAMPLE={1f,1.125f,1.25f,1.375f,1.5f,1.625f,1.75f,1.875f,2f,2.125f,2.25f,2.375f,2.5f,2.625f,2.75f,2.875f,3f,3.25f,3.5f,3.75f,4f,4.5f,5f,5.5f,6f};
    /** False: Minecraft's pixel font (menu, HUD). True: the bundled sans-serif (dialogs). */
    boolean sans;
    /** Draw every weight as regular (used by dialogs). */
    boolean noBold;
    float fontScale=1;

    private Text styled(String s,boolean boldWanted,float k){
        boolean bold=boldWanted&&!noBold;
        if(!sans)return STYLED.computeIfAbsent((bold?"b|":"r|")+s,x->Text.literal(s).setStyle(Style.EMPTY.withBold(bold)));
        int key=Math.round(k*1000);
        return STYLED.computeIfAbsent((bold?"sb":"sr")+key+"|"+s,x->Text.literal(s).setStyle(Style.EMPTY.withFont(
            new StyleSpriteSource.Font(Identifier.of("shoremod","px_"+(bold?"b":"r")+"_"+key)))));
    }
    /**
     * Screen pixels per font unit for text of the given logical size. The pixel font uses whole
     * numbers so it stays sharp; the sans font snaps to the nearest pre-baked oversample variant and is
     * drawn at exactly that scale, so its glyph texels map 1:1 to screen pixels.
     */
    private float snap(float size){
        size*=fontScale;
        if(!sans)return Math.max(1,Math.round(size*u/9f));
        float want=size*u/11f,best=OVERSAMPLE[0];
        for(float k:OVERSAMPLE)if(Math.abs(k-want)<Math.abs(best-want))best=k;
        return best;
    }
    /** Width in logical units at the snapped size {@link #text} draws with. */
    float width(String s,boolean bold,float size){float k=snap(size);return mc.textRenderer.getWidth(styled(s,bold,k))*k/u;}
    /** Draws text vertically centered in an em box of height {@code size} starting at y. Light pixel-font text gets vanilla's shadow. */
    void text(String s,boolean bold,float size,float x,float y,int color){
        int c=color&0xFFFFFF;boolean lightText=((c>>16&255)*299+(c>>8&255)*587+(c&255)*114)/1000>120;
        draw(s,bold,size,x,y,color,lightText&&!sans);
    }
    void textShadow(String s,boolean bold,float size,float x,float y,int color){draw(s,bold,size,x,y,color,true);}
    private void draw(String s,boolean bold,float size,float x,float y,int color,boolean shadow){
        if(s.isEmpty())return;
        float k=snap(size);
        var m=ctx.getMatrices();
        m.pushMatrix();
        float glyphH=sans?11f*0.72f:8f; // cap height of the sans font vs the pixel font's 8 px
        float offset=sans?(size*u-glyphH*k)/2f+k*1.0f:(size*u-8*k)/2f;
        m.translate(p(x),Math.round(y*u+offset));
        m.scale(k,k);
        ctx.drawText(mc.textRenderer,styled(s,bold,k),0,0,color,shadow);
        m.popMatrix();
    }
    void text(String s,boolean bold,float size,float x,float y,int color,float maxW){text(fit(s,bold,size,maxW),bold,size,x,y,color);}
    void centered(String s,boolean bold,float size,float cx,float y,int color,float maxW){
        String f=fit(s,bold,size,maxW);text(f,bold,size,cx-width(f,bold,size)/2,y,color);
    }
    String fit(String s,boolean bold,float size,float maxW){
        if(maxW<=0||width(s,bold,size)<=maxW)return s;
        int lo=0,hi=s.length();
        while(lo<hi){int mid=(lo+hi+1)/2;if(width(s.substring(0,mid).stripTrailing()+"...",bold,size)<=maxW)lo=mid;else hi=mid-1;}
        return s.substring(0,lo).stripTrailing()+"...";
    }

    void scissor(float x,float y,float w,float h){ctx.enableScissor(p(x),p(y),p(x+w),p(y+h));}
    void endScissor(){ctx.disableScissor();}

    static int mix(int a,int b,float t){
        t=Math.clamp(t,0f,1f);
        int aa=a>>>24,ar=a>>16&255,ag=a>>8&255,ab=a&255,ba=b>>>24,br=b>>16&255,bg=b>>8&255,bb=b&255;
        return Math.round(aa+(ba-aa)*t)<<24|Math.round(ar+(br-ar)*t)<<16|Math.round(ag+(bg-ag)*t)<<8|Math.round(ab+(bb-ab)*t);
    }
    static int alpha(int color,float a){return Math.round((color>>>24)*a)<<24|(color&0xFFFFFF);}
}

package net.shoresmp.fabric;
import net.shoresmp.core.FrameRing;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class Mp4Test {
    public static void main(String[] args) throws Exception {
        Path dir=Path.of("build/mp4-test");Files.createDirectories(dir);
        var frames=new ArrayList<FrameRing.Frame>();
        for(int i=0;i<3;i++) {
            var image=new BufferedImage(64,48,BufferedImage.TYPE_INT_RGB);
            var g=image.createGraphics();g.setColor(new java.awt.Color(i*90,70,180));g.fillRect(0,0,64,48);g.dispose();
            var out=new ByteArrayOutputStream();ImageIO.write(image,"jpg",out);
            frames.add(new FrameRing.Frame(1_000_000_000L+i*400_000_000L,out.toByteArray(),64,48));
        }
        for(int fps:new int[]{24,60}) Mp4Clip.write(dir.resolve("clip-"+fps+".mp4"),frames,fps,2_000_000_000L);
        Path bad=dir.resolve("invalid.mp4");
        try {Mp4Clip.write(bad,List.of(new FrameRing.Frame(0,new byte[]{1},64,48)),60,1);throw new AssertionError("Invalid frame accepted");}
        catch(IOException expected) {if(Files.exists(bad))throw new AssertionError("Partial export left behind");}
        System.out.println("PASS MP4 export and failed export cleanup");
    }
}

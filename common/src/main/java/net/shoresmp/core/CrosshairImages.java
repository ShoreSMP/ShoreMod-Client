package net.shoresmp.core;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.*;
import javax.imageio.*;
/** Validates headers before decoding; area-resizes large sources to square RGBA crosshairs. */
public final class CrosshairImages {
 private CrosshairImages(){}
 public static BufferedImage read(Path file)throws IOException{
  if(!Files.isRegularFile(file)||Files.size(file)>16L*1024*1024)throw new IOException("Choose an image under 16 MiB");
  try(var input=ImageIO.createImageInputStream(file.toFile())){
   if(input==null)throw new IOException("Could not open image");
   var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Use PNG or JPEG; this format is not supported");
   var reader=readers.next();
   try{
    reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);
    if(w<1||h<1||w>8192||h>8192)throw new IOException("Image dimensions must be at most 8192x8192");
    if((long)w*h>8_388_608L)throw new IOException("Image is too large; use at most 8 megapixels");
    var image=reader.read(0);if(image==null)throw new IOException("Image could not be decoded");
    double scale=Math.min(1,256.0/Math.max(w,h));int width=Math.max(1,(int)Math.round(w*scale)),height=Math.max(1,(int)Math.round(h*scale)),side=Math.max(width,height);
    var rgba=new BufferedImage(side,side,BufferedImage.TYPE_INT_ARGB);
    // Area averages retain thin lines that nearest-neighbor subsampling can drop entirely.
    for(int y=0;y<height;y++)for(int x=0;x<width;x++){
     long a=0,red=0,green=0,blue=0,count=0;
     for(int sy=y*h/height;sy<(y+1)*h/height;sy++)for(int sx=x*w/width;sx<(x+1)*w/width;sx++){
      int pixel=image.getRGB(sx,sy),alpha=pixel>>>24;a+=alpha;red+=(pixel>>16&255)*alpha;green+=(pixel>>8&255)*alpha;blue+=(pixel&255)*alpha;count++;
     }
     int pixel=a==0?0:((int)((a+count/2)/count)<<24)|((int)(red/a)<<16)|((int)(green/a)<<8)|(int)(blue/a);
     rgba.setRGB(x+(side-width)/2,y+(side-height)/2,pixel);
    }
    image.flush();
    return rgba;
   }finally{reader.dispose();}
  }
 }
}

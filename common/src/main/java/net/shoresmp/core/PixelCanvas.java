package net.shoresmp.core;
import java.util.*;
/** Bounded 32x32 pixel art with stroke-level undo; independent of game APIs. */
public final class PixelCanvas {
 public static final int SIZE=32,COUNT=SIZE*SIZE;
 private int[] pixels;
 private final Deque<int[]> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
 private int[] before;
 public PixelCanvas(int[] initial){pixels=initial!=null&&initial.length==COUNT?initial.clone():new int[COUNT];}
 public int get(int x,int y){return inside(x,y)?pixels[y*SIZE+x]:0;}
 public int[] copy(){return pixels.clone();}
 public static boolean inside(int x,int y){return x>=0&&y>=0&&x<SIZE&&y<SIZE;}
 public void begin(){if(before==null)before=pixels.clone();}
 public void end(){if(before!=null&&!Arrays.equals(before,pixels)){undo.addLast(before);while(undo.size()>40)undo.removeFirst();redo.clear();}before=null;}
 public void line(int x0,int y0,int x1,int y1,int color){
  if(!inside(x0,y0)||!inside(x1,y1))return;
  int dx=Math.abs(x1-x0),sx=x0<x1?1:-1,dy=-Math.abs(y1-y0),sy=y0<y1?1:-1,error=dx+dy;
  while(true){pixels[y0*SIZE+x0]=color;if(x0==x1&&y0==y1)break;int e=error*2;if(e>=dy){error+=dy;x0+=sx;}if(e<=dx){error+=dx;y0+=sy;}}
 }
 public void clear(){begin();Arrays.fill(pixels,0);end();}
 public void undo(){end();if(!undo.isEmpty()){redo.addLast(pixels);pixels=undo.removeLast();}}
 public void redo(){end();if(!redo.isEmpty()){undo.addLast(pixels);pixels=redo.removeLast();}}
}

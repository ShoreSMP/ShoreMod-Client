package net.shoresmp.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.texture.NativeImage;
import net.shoresmp.core.*;

import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class ClipRecorder implements AutoCloseable {
    private final FrameRing ring = new FrameRing(30_000_000_000L, 96L*1024*1024);
    private final ExecutorService frames = Executors.newSingleThreadExecutor(r -> {var t=new Thread(r,"ShoreMod-frames");t.setDaemon(true);return t;});
    private final ExecutorService exports = Executors.newSingleThreadExecutor(r -> {var t=new Thread(r,"ShoreMod-export");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
    private final AtomicBoolean capturePending = new AtomicBoolean(), exporting = new AtomicBoolean();
    private final AtomicInteger epoch = new AtomicInteger();
    private long lastTrigger;
    private long captureRequests;
    public long captureRequests(){return captureRequests;}
    private volatile long lastGameplay;
    private volatile Path lastClip;
    private volatile String status = "Buffer warming up";
    private volatile boolean closed;
    private String recordingSession;
    private final CaptureBudget budget=new CaptureBudget();
    // Accessed only by the single encoding worker, reused between frames.
    private BufferedImage scaledBuffer;
    private ImageWriter jpegWriter;
    private final ByteArrayOutputStream compressed = new ByteArrayOutputStream(64 * 1024);
    private int[] sourceColumns = new int[0];
    private int sourceWidth;
    public Path directory() { return MinecraftClient.getInstance().runDirectory.toPath().resolve("shoremod-clips"); }
    public Path lastClip() { return lastClip; }
    public String status() { return ShoreClient.config.lowOverhead ? "Paused by performance mode" : ShoreClient.config.recording ? status : "Recording disabled"; }
    public void beginSession() {
        var client=MinecraftClient.getInstance();
        String server=client.getCurrentServerEntry()==null?"singleplayer":client.getCurrentServerEntry().address;
        String session=client.getSession().getUuidOrNull()+"@"+server.toLowerCase(Locale.ROOT);
        // Preserve short transfers only for the same account and server address.
        if (!session.equals(recordingSession) || System.nanoTime()-lastGameplay > 10_000_000_000L) clear();
        recordingSession=session;
        ShoreClient.tell(ShoreClient.config.recording ? "Gameplay clip buffer enabled: up to "+ShoreClient.config.clipSeconds+" seconds, stored locally. Use the Save clip key to keep a moment." : "Gameplay recording is disabled. Change it in /shore.");
    }
    public synchronized void clear() { epoch.incrementAndGet(); ring.clear(); lastTrigger=0; lastGameplay=0; budget.reset(); }
    private synchronized void addFrame(int captureEpoch,FrameRing.Frame frame,long durationNanos) {
        if(!closed && captureEpoch==epoch.get()) ring.add(frame,durationNanos);
    }
    /** Called after the entire world and GUI have rendered, on the render thread. */
    public void capture() {
        MinecraftClient client=MinecraftClient.getInstance();
        if(closed || !ShoreClient.config.recording || ShoreClient.config.lowOverhead || client.world==null) return;
        long now=System.nanoTime();
        budget.observe(now);
        if(client.currentScreen!=null){status="Paused in menus; gameplay capture resumes on close";return;}
        lastGameplay=now;
        if(ShoreClient.config.recordingProtectFps&&budget.protectingSlowFrames()){status="Paused to protect low FPS";return;}
        if(capturePending.get() || !budget.allow(now,ShoreClient.config.clipFps,(long)client.getWindow().getFramebufferWidth()*client.getWindow().getFramebufferHeight(),ShoreClient.config.recordingProtectFps) || !capturePending.compareAndSet(false,true))return;
        int captureEpoch=epoch.get(); int maxWidth=ShoreClient.config.clipWidth;
        int quality=ShoreClient.config.clipJpegQuality;
        long durationNanos=ShoreClient.config.clipSeconds*1_000_000_000L;
        try {
            captureRequests++;
            ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> {
                if(closed) {image.close();capturePending.set(false);return;}
                try {
                    frames.execute(() -> {
                        long encodingStarted=System.nanoTime();
                        try(image) {
                            if(captureEpoch!=epoch.get()) return;
                            int inputWidth=image.getWidth(), inputHeight=image.getHeight();
                            if(inputWidth<2||inputHeight<2)return;
                            int w=Math.min(maxWidth,inputWidth);
                            int h=Math.max(2,(int)((double)inputHeight*w/inputWidth));
                            w=Math.max(2,w/2*2); h=h/2*2;
                            if(scaledBuffer==null||scaledBuffer.getWidth()!=w||scaledBuffer.getHeight()!=h)scaledBuffer=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
                            BufferedImage scaled=scaledBuffer;
                            int[] pixels=((java.awt.image.DataBufferInt)scaled.getRaster().getDataBuffer()).getData();
                            if(sourceColumns.length!=w||sourceWidth!=inputWidth){
                                sourceWidth=inputWidth;
                                sourceColumns=new int[w];
                                for(int x=0;x<w;x++)sourceColumns[x]=(int)((long)x*inputWidth/w);
                            }
                            int index=0;
                            for(int y=0;y<h;y++){
                                int sourceY=(int)((long)y*inputHeight/h);
                                for(int sourceX:sourceColumns)pixels[index++]=image.getColorArgb(sourceX,sourceY);
                            }
                            byte[] jpeg=compress(scaled,quality);
                            addFrame(captureEpoch,new FrameRing.Frame(now,jpeg,w,h),durationNanos);
                            status="Buffer active: "+w+"x"+h+(ShoreClient.config.recordingProtectFps?" (FPS protection)":" at "+ShoreClient.config.clipFps+" FPS");
                        } catch(Exception e) { status="Capture error"; ShoreClient.LOG.warn("Clip frame failed",e); }
                        finally {budget.processed(System.nanoTime()-encodingStarted);capturePending.set(false);}
                    });
                } catch(RejectedExecutionException e) {image.close();capturePending.set(false);}
            });
            budget.requested(System.nanoTime()-now);
        } catch(RuntimeException e) {capturePending.set(false);ShoreClient.LOG.warn("Framebuffer capture failed",e);}
    }
    private byte[] compress(BufferedImage image,int quality) throws IOException {
        if(jpegWriter==null)jpegWriter=ImageIO.getImageWritersByFormatName("jpeg").next();
        var writer=jpegWriter;
        compressed.reset();
        try(var out=new MemoryCacheImageOutputStream(compressed)) {
            writer.setOutput(out); var param=writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);param.setCompressionQuality(quality/100f);
            writer.write(null,new IIOImage(image,null,null),param);
        } finally {writer.reset();}
        return compressed.toByteArray();
    }
    public void disconnected(String reason) {
        // Disconnect reasons belong to this client; staff system-chat broadcasts do not.
        if(ShoreClient.config.saveEveryDisconnect || PunishmentMatcher.matches(reason,ShoreClient.config.punishmentPhrases))
            trigger(reason,true);
    }
    public void trigger(String reason,boolean explicit) {
        save(reason,explicit,false);
    }
    public void saveManual() {
        save("Manually saved gameplay",true,true);
    }
    private void save(String reason,boolean explicit,boolean manual) {
        if(closed)return;
        if(!ShoreClient.config.recording || ShoreClient.config.lowOverhead){
            if(manual)ShoreClient.tell("Enable Clip Recorder to buffer gameplay.");
            return;
        }
        if(!explicit && !PunishmentTarget.matchesChat(reason,ShoreClient.config.punishmentPhrases,
            MinecraftClient.getInstance().getSession().getUsername())) return;
        long now=System.nanoTime();
        if(lastGameplay==0 || now-lastGameplay>15_000_000_000L){
            if(manual)ShoreClient.tell("No recent gameplay buffered yet.");
            return;
        }
        if(!manual && lastTrigger!=0 && now-lastTrigger<30_000_000_000L)return;
        if(!exporting.compareAndSet(false,true)){
            if(manual)ShoreClient.tell("Your previous clip is still saving.");
            return;
        }
        if(!manual)lastTrigger=now;
        var client=MinecraftClient.getInstance();
        String server=client.getCurrentServerEntry()==null?"unknown":client.getCurrentServerEntry().address;
        boolean privacy=ShoreClient.config.privacy;
        int fps=ShoreClient.config.clipFps;
        int seconds=ShoreClient.config.clipSeconds;
        boolean popup=ShoreClient.config.clipPopup;
        int storage=ShoreClient.config.maxClipStorageMb;
        String ffmpeg=ShoreClient.config.ffmpegPath;
        String notificationSession=recordingSession;
        try {
        frames.execute(() -> {
            var snapshot=ring.snapshot(now,seconds*1_000_000_000L);
            try {
            exports.execute(() -> {
                try {
                    if(snapshot.isEmpty()) throw new IOException("No gameplay frames buffered yet");
                    Files.createDirectories(directory());
                    String name="shoremod-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
                    status="Saving MP4 clip…";
                    // A delayed trigger should not append seconds of frozen footage after the last frame.
                    long end=Math.min(now,snapshot.getLast().nanos()+1_000_000_000L/fps);
                    Path clip=exportMp4(directory().resolve(name+".mp4"),snapshot,fps,end,ffmpeg);
                    double duration=Math.max(1.0/fps,Math.ceil((end-snapshot.getFirst().nanos())*1e-9*fps)/fps);
                    Files.writeString(directory().resolve(name+".txt"),"ShoreMod gameplay evidence\nTime: "+Instant.now()+"\nReason: "+reason
                        +"\nServer: "+(privacy?"Hidden by privacy settings":server)+"\nRecorded duration: "+String.format(Locale.ROOT,"%.2f",duration)
                        +" seconds\nCaptured frames: "+snapshot.size()+"\nOutput frame rate: "+fps+" FPS\nVideo only; no microphone or game audio.\n");
                    lastClip=clip; prune(storage);
                    client.execute(() -> {
                        ShoreClient.tell("Gameplay clip saved to shoremod-clips/"+clip.getFileName());
                        if(popup && !closed && Objects.equals(notificationSession,recordingSession))
                            client.setScreen(new ClipSavedScreen(client.currentScreen,clip,duration));
                    });
                } catch(Exception e) {ShoreClient.LOG.error("Clip export failed",e);ShoreClient.tellError("Clip could not be saved: "+e.getMessage());}
                finally {exporting.set(false);}
            });
            } catch(RejectedExecutionException e) { exporting.set(false); }
        });
        } catch(RejectedExecutionException e) { exporting.set(false); }
    }
    private Path exportMp4(Path mp4,List<FrameRing.Frame> snapshot,int fps,long now,String executable) throws IOException,InterruptedException {
        Path temp=mp4.resolveSibling(mp4.getFileName()+".part.mp4");
        Path avi=mp4.resolveSibling(mp4.getFileName()+".source.avi.part");
        Process process=null;
        try {
            boolean converted=false;
            if(!executable.isBlank()) {
                try {
                    MjpegAvi.write(avi,snapshot,fps,now);
                    process=new ProcessBuilder(executable,"-nostdin","-y","-i",avi.toString(),"-c:v","libx264","-threads","1","-preset","veryfast","-crf","23","-pix_fmt","yuv420p","-movflags","+faststart",temp.toString())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    if(!process.waitFor(90,TimeUnit.SECONDS)) {
                        process.destroyForcibly();process.waitFor();throw new IOException("MP4 conversion timed out");
                    }
                    converted=process.exitValue()==0 && Files.isRegularFile(temp) && Files.size(temp)>0;
                } catch(IOException e) {ShoreClient.LOG.warn("Using built-in MP4 encoder after configured encoder failed",e);}
            }
            if(!converted) Mp4Clip.write(temp,snapshot,fps,now);
            Files.move(temp,mp4,StandardCopyOption.REPLACE_EXISTING);
            return mp4;
        } catch(InterruptedException e) {
            if(process!=null)process.destroyForcibly();
            Thread.currentThread().interrupt();throw e;
        } finally {Files.deleteIfExists(avi);Files.deleteIfExists(temp);}
    }
    private void prune(int maxMb) throws IOException {
        try(var stream=Files.list(directory())) {
            var clips=stream.filter(p->p.getFileName().toString().matches("shoremod-.*\\.(avi|mp4)"))
                .sorted(Comparator.comparing(p->p.getFileName().toString())).toList();
            long bytes=0;for(Path p:clips)bytes+=Files.size(p);
            for(Path p:clips) {
                if(bytes<=maxMb*1024L*1024L)break;
                if(p.equals(lastClip))continue;
                long size=Files.size(p);Files.delete(p);bytes-=size;
                String base=p.getFileName().toString().replaceFirst("\\.(avi|mp4)$","");Files.deleteIfExists(p.resolveSibling(base+".txt"));
            }
        }
    }
    @Override public synchronized void close() {
        if(closed)return;
        closed=true;
        // Existing exports finish; queued captures observe closed and cannot repopulate the ring.
        clear();
        frames.execute(()->{if(jpegWriter!=null)jpegWriter.dispose();scaledBuffer=null;exports.shutdown();});
        frames.shutdown();ring.clear();
    }
}

package net.shoresmp.core;

import java.util.*;
import java.math.*;
import java.nio.file.*;
import java.nio.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;

public final class CoreTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        for(int gui:new int[]{1,2,3,4,6}){
            float normal=HudScale.guiUnits(1080,gui,100,100)*gui;
            float smaller=HudScale.guiUnits(1080,gui,50,100)*gui;
            check(Math.abs(normal-1.5f)<0.0001f,"HUD physical size is independent of Minecraft GUI zoom");
            check(Math.abs(smaller-normal/2)<0.0001f,"overall HUD size shrinks complete geometry by half");
            check(Math.abs(HudScale.guiUnits(1080,gui,100,50)*gui-smaller)<0.0001f,"per-element scaling uses the same box/icon/text geometry");
        }
        check(Payments.amount("5b").equals(new BigDecimal("5000000000.00")),"billion input expands exactly");
        check(Payments.amount("2.5M").equals(new BigDecimal("2500000.00")),"case-insensitive decimal suffix");
        for(String n:List.of("0.01","999.99","1k","1.25m","5b","1t","1000t","5.000000001b")){
            var amount=Payments.amount(n);check(Payments.amount(Payments.format(amount)).compareTo(amount)==0,"compact display is exact: "+n);
        }
        check(Payments.format(Payments.amount("5b")).equals("5b"),"round billions display without zeros");
        for(String bad:List.of("-5b","5bb","1e9","1001t","0b","0.001","1;op","999999999999999999999b")){
            try{Payments.amount(bad);throw new AssertionError("Invalid compact amount accepted");}catch(IllegalArgumentException expected){checks++;}
        }
        check(Wire.isHello(Wire.hello()),"server accepts exact hello");
        check(!Wire.isHello(null)&&!Wire.isHello(Wire.clip("reason")),"client cannot issue a clip instruction");
        check(!Wire.isHello(Wire.roster(1,List.of()).getFirst()),"client cannot issue a roster");
        var fuzz=new Random(713);for(int i=0;i<2000;i++){byte[] data=new byte[fuzz.nextInt(0,100)];fuzz.nextBytes(data);check(Wire.isHello(data)==(data.length==2&&data[0]==1&&data[1]==0),"hello gate rejects arbitrary packets");}
        var pixels=new PixelCanvas(null);pixels.begin();pixels.line(0,0,31,31,0xFF12AB34);pixels.end();
        check(pixels.get(0,0)==0xFF12AB34&&pixels.get(31,31)==0xFF12AB34,"painting fills a continuous stroke");
        pixels.undo();check(pixels.get(0,0)==0,"undo reverts entire stroke");pixels.redo();check(pixels.get(31,31)==0xFF12AB34,"redo restores stroke");
        pixels.clear();pixels.undo();check(pixels.get(31,31)==0xFF12AB34,"clear can be undone");
        var copied=pixels.copy();copied[0]=0;check(pixels.get(0,0)==0xFF12AB34,"saved snapshots cannot mutate canvas");
        pixels.line(-1,0,999,999,0);check(pixels.get(31,31)==0xFF12AB34,"out of bounds strokes are ignored");
        var budget=new CaptureBudget();int captures=0;
        for(long now=1;now<1_000_000_000L;now+=8_333_333L){budget.observe(now);if(budget.allow(now,30,1920L*1080,true))captures++;}
        check(captures>0&&captures<=9,"protected recording is capped near eight readbacks per second");
        budget.reset();int highRate=0;for(long now=1;now<1_000_000_000L;now+=1_000_000L){budget.observe(now);if(budget.allow(now,60,1920L*1080,false))highRate++;}
        check(highRate>=58&&highRate<=60,"explicit high-rate mode supports sixty capture requests per second");
        budget.reset();check(!budget.allow(1,30,33_177_600,true),"oversized framebuffers cannot allocate giant capture images");
        budget.reset();budget.requested(100_000_000L);check(budget.allow(1,30,1920L*1080,true)&&!budget.allow(500_000_001L,30,1920L*1080,true),"costly GPU requests force longer capture intervals");
        budget.reset();for(long now=1;now<10_000_000_000L;now+=66_666_667L)budget.observe(now);
        check(!budget.allow(10_000_000_000L,30,1920L*1080,true),"recording pauses when rendering is already very slow");
        var importSource=Files.createTempFile("crosshair-input-",".png");
        try{
            var large=new BufferedImage(1024,512,BufferedImage.TYPE_INT_ARGB);large.setRGB(0,0,0xFF50A7FF);ImageIO.write(large,"png",importSource.toFile());
            var normalized=CrosshairImages.read(importSource);
            check(normalized.getWidth()<=256&&normalized.getHeight()<=256,"large images are downsampled before activation");
            check(normalized.getWidth()==normalized.getHeight()&&(normalized.getRGB(0,64)&0xFFFFFF)==0x50A7FF&&(normalized.getRGB(0,64)>>>24)>0&&(normalized.getRGB(10,10)>>>24)==0,"thin pixels, aspect ratio, color and transparency survive image import");
            Files.writeString(importSource,"not an image");try{CrosshairImages.read(importSource);throw new AssertionError("Invalid image accepted");}catch(IOException expected){checks++;}
        }finally{Files.deleteIfExists(importSource);}
        check(SettingValues.integer("173",50,250).orElse(-1)==173,"custom values need not follow slider increments");
        for(String bad:List.of("", "-1", "251", "NaN", "1e2", "999999999", "73px"))
            check(SettingValues.integer(bad,50,250).isEmpty(),"invalid numeric draft is rejected");
        check(SettingValues.integer("50",50,250).isPresent()&&SettingValues.integer("250",50,250).isPresent(),"inclusive value limits");
        check(SettingValues.color("#12aB34").orElse(-1)==0x12AB34,"custom hex color");
        check(SettingValues.color("ABCDEF").orElse(-1)==0xABCDEF,"hex without hash");
        for(String bad:List.of("", "#", "FFFFFFF", "GGGGGG", "#FF", "0xFFFFFF"))check(SettingValues.color(bad).isEmpty(),"invalid colors rejected");
        check(SettingValues.brightness(0.4,0)==0.4,"zero boost preserves brightness");
        check(SettingValues.brightness(0.4,100)==16,"fullbright is bounded");
        check(SettingValues.brightness(0.4,999)==16,"edited config cannot exceed fullbright cap");
        check(Double.isFinite(SettingValues.brightness(Double.NaN,50)),"invalid base gamma cannot reach renderer");
        for(int i=1;i<=100;i++)check(SettingValues.brightness(0.4,i)>=SettingValues.brightness(0.4,i-1),"brightness strength is monotonic");
        check(ModUpdates.newer("0.2.10-dev","0.2.9-dev"),"update numeric ordering");
        check(!ModUpdates.newer("0.2.8-dev","0.2.8-dev"),"same version not newer");
        check(!ModUpdates.newer("0.2.7-dev","0.2.8-dev"),"no downgrade notice");
        check(ModUpdates.newer("0.2.8","0.2.8-dev"),"stable newer than development");
        check(!ModUpdates.newer("0.2.8-dev","0.2.8"),"development older than stable");
        check(!ModUpdates.newer("bad","0.2.8"),"invalid version ignored");
        check(ModUpdates.isRequest(ModUpdates.request()),"update request roundtrip");
        check(!ModUpdates.isRequest(new byte[]{1,0,0}),"bounded request");
        var notice=new ModUpdates.Notice("0.2.9-dev","https://shoresmp.net/download");
        check(ModUpdates.decode(ModUpdates.encode(notice)).equals(notice),"update notice roundtrip");
        for(String url:List.of("javascript:alert(1)","http://shoresmp.net","https://user@shoresmp.net","https://shoresmp.net:444"))check(!ModUpdates.validUrl(url),"blocked update URL");
        check(ModUpdates.validUrl(""),"download link optional");
        byte[] updateBytes=ModUpdates.encode(notice);
        try{ModUpdates.decode(Arrays.copyOf(updateBytes,updateBytes.length+1));throw new AssertionError("trailing update data accepted");}catch(IOException expected){checks++;}
        try{ModUpdates.decode(new byte[]{1,1,0,0});throw new AssertionError("truncated update accepted");}catch(IOException expected){checks++;}
        check(TextureSeed.scramble(123,456)==TextureSeed.scramble(123,456),"stable texture variation while enabled");
        check(TextureSeed.scramble(123,456)!=TextureSeed.scramble(123,789),"a fresh toggle seed changes texture variation");
        check(TextureSeed.scramble(123,456)!=TextureSeed.scramble(124,456),"texture positions remain distinct");
        var rotations=new HashSet<Long>();for(int i=0;i<256;i++)rotations.add(TextureSeed.scramble(i,456)&3);
        check(rotations.size()==4,"all rotation choices available");
        var users = new ArrayList<Wire.User>();
        for (int i = 0; i < 1250; i++) users.add(new Wire.User(UUID.randomUUID(), "Player" + i));
        var pages = new ArrayList<>(Wire.roster(3, users)); Collections.reverse(pages);
        var roster = new Roster();
        for (byte[] page : pages) roster.accept(Wire.decode(page));
        check(roster.users().size() == 1250, "paginated roster");
        var snapshot=roster.users();roster.accept(Wire.decode(pages.getFirst()));check(roster.users()==snapshot,"unchanged presence heartbeat avoids rebuilding the snapshot");
        roster.accept(Wire.decode(Wire.roster(2, List.of()).getFirst()));
        check(roster.users().size() == 1250, "stale snapshot ignored");
        roster.accept(Wire.decode(Wire.roster(4, List.of()).getFirst()));
        check(roster.users().isEmpty(), "empty snapshot clears");
        check(Wire.decode(Wire.hello()).op() == Wire.HELLO, "hello roundtrip");
        check(Wire.decode(Wire.clip("bug abusing")).reason().equals("bug abusing"), "clip roundtrip");
        try { Wire.decode(new byte[]{1,0,9}); throw new AssertionError("trailing accepted"); } catch (IOException expected) { checks++; }
        try { Wire.decode(new byte[]{1,1}); throw new AssertionError("truncated accepted"); } catch (IOException expected) { checks++; }
        var three = users.subList(0, 3);
        for (int seed = 0; seed < 500; seed++) {
            var plan = Payments.split(three, Payments.amount("10.01"), new Random(seed));
            check(Payments.total(plan).equals(new BigDecimal("10.01")), "rain conserves budget");
            check(plan.stream().allMatch(p -> p.amount().signum() > 0), "positive shares");
        }
        check(Payments.total(Payments.split(three, Payments.amount("10"), null)).equals(new BigDecimal("10.00")), "split conserves budget");
        for (String invalid : List.of("-1", "0", "NaN", "1e5", "1.001", "x", "1;pay")) {
            try { Payments.amount(invalid); throw new AssertionError("amount accepted"); } catch (IllegalArgumentException expected) { checks++; }
        }
        var triggers = List.of("blacklisted modifications", "bug abusing", "banned");
        check(PunishmentMatcher.matches("§cBLACKLISTED MODIFICATIONS", triggers), "formatted trigger");
        check(!PunishmentMatcher.matches("You are unbanned", triggers), "word boundaries");
        var personalReasons=List.of("blacklisted modifications","bug abusing","cheating","exploiting","banned");
        for(String message:List.of(
            "You have been banned. Reason: blacklisted modifications",
            "[ShoreSMP] You were permanently banned for bug abusing",
            "\u00a7cYou are banned!\nReason: Bug Abusing",
            "You\u2019re temporarily banned for cheating",
            "Your account has been suspended for exploiting",
            "You have been sent back to the hub. Reason: bug abusing",
            "You were returned to the lobby for blacklisted modifications",
            "ExamplePlayer was banned for bug abusing",
            "[ShoreSMP] Player ExamplePlayer has been banned for cheating",
            "Player: ExamplePlayer\nReason: bug abusing",
            "Target: EXAMPLEPLAYER\nReason: blacklisted modifications"))
            check(PunishmentTarget.matchesChat(message,personalReasons,"ExamplePlayer"),"personal punishment: "+message);
        for(String message:List.of(
            "[Staff] OtherPlayer was banned for bug abusing",
            "[ShoreSMP] [STAFF] OtherPlayer was banned for blacklisted modifications",
            "OtherPlayer was banned by ExamplePlayer for cheating",
            "ExamplePlayer banned OtherPlayer for bug abusing",
            "You banned OtherPlayer for blacklisted modifications",
            "You have banned OtherPlayer for cheating",
            "Player: OtherPlayer\nStaff: ExamplePlayer\nReason: bug abusing",
            "Target: OtherPlayer\nReason: blacklisted modifications",
            "ExamplePlayer2 was banned for bug abusing",
            "OtherPlayer: You have been banned for cheating",
            "[Staff] You have been banned for cheating",
            "[Staff] ExamplePlayer was banned for bug abusing",
            "Staff report: ExamplePlayer was banned for cheating",
            "Reason: blacklisted modifications",
            "bug abusing",
            "You are unbanned",
            "You have been sent to the hub for scheduled maintenance"))
            check(!PunishmentTarget.matchesChat(message,personalReasons,"ExamplePlayer"),"staff/other-player message: "+message);
        check(!PunishmentTarget.matchesChat(null,personalReasons,"ExamplePlayer"),"null personal message");
        check(!PunishmentTarget.matchesChat("Player: Other\nReason: cheating",personalReasons,null),"missing player identity");
        check(PunishmentMatcher.matches("Reason: blacklisted modifications",personalReasons),"disconnect reason remains sufficient");
        var ring = new FrameRing(30, 20);
        ring.add(new FrameRing.Frame(10,new byte[8],2,2)); ring.add(new FrameRing.Frame(20,new byte[8],2,2)); ring.add(new FrameRing.Frame(45,new byte[8],2,2));
        check(ring.snapshot(45).size() == 2, "ring bounds");
        check(ring.snapshot(19).isEmpty(), "snapshot excludes future frames");
        ring.add(new FrameRing.Frame(50,new byte[8],4,4)); check(ring.snapshot(50).size()==1, "resize reset");
        var bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB), "jpg", bytes);
        var video = Files.createTempFile("shoremod-test-", ".avi");
        try {
            MjpegAvi.write(video,List.of(new FrameRing.Frame(0,bytes.toByteArray(),32,32),new FrameRing.Frame(1_000_000_000L,bytes.toByteArray(),32,32)),10,2_000_000_000L);
            byte[] avi=Files.readAllBytes(video); var buffer=ByteBuffer.wrap(avi).order(ByteOrder.LITTLE_ENDIAN);
            check(new String(avi,0,4).equals("RIFF"), "AVI signature");
            check(buffer.getInt(4)==avi.length-8, "RIFF size");
            check(buffer.getInt(48)==20, "AVI frame count");
            String text=new String(avi,java.nio.charset.StandardCharsets.ISO_8859_1);
            check(text.contains("movi") && text.contains("idx1") && text.contains("MJPG"), "AVI stream and index");
            MjpegAvi.write(video,List.of(new FrameRing.Frame(0,bytes.toByteArray(),32,32)),60,2_000_000_000L);
            var highFpsAvi=ByteBuffer.wrap(Files.readAllBytes(video)).order(ByteOrder.LITTLE_ENDIAN);
            check(highFpsAvi.getInt(48)==120&&highFpsAvi.getInt(32)==1_000_000/60,"sixty FPS AVI declares correct timing and frame count");
        } finally { Files.deleteIfExists(video); }
        System.out.println("PASS: " + checks + " core checks");
    }
}


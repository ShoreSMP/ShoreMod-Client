package net.shoresmp.fabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.util.*;
import java.util.function.*;

/**
 * Developer-only self test, inert unless the JVM is started with -Dshoremod.selftest=ui|world
 * (see the selfTestUi / selfTestWorld Loom run configs). It screenshots every settings tab, and in
 * a throwaway world drives the inventory tweaks with real slot clicks and checks the results.
 */
final class ShoreSelfTest {
    private ShoreSelfTest(){}
    private record Step(int delay,Predicate<MinecraftClient> ready,Consumer<MinecraftClient> action){}
    private static final Deque<Step> steps=new ArrayDeque<>();
    private static final List<String> report=new ArrayList<>();
    private static int countdown=-1;

    static void register(){
        String mode=System.getProperty("shoremod.selftest","");
        if(mode.isEmpty())return;
        ShoreClient.LOG.warn("ShoreMod self test enabled: {}",mode);
        if(mode.equals("ui"))planUi();else if(mode.equals("world"))planWorld();else if(mode.equals("creative"))planCreative();else if(mode.equals("hud"))planHud();
        ClientTickEvents.END_CLIENT_TICK.register(ShoreSelfTest::tick);
    }
    private static void step(int delay,Consumer<MinecraftClient> action){steps.add(new Step(delay,c->true,action));}
    private static void await(Predicate<MinecraftClient> ready,int delay){steps.add(new Step(delay,ready,c->{}));}
    private static void tick(MinecraftClient client){
        Step s=steps.peek();if(s==null)return;
        if(countdown<0)countdown=s.delay();
        if(countdown>0){countdown--;return;}
        if(!s.ready().test(client))return;
        steps.poll();countdown=-1;
        try{s.action().accept(client);}catch(Throwable t){log("ERROR in step: "+t);ShoreClient.LOG.error("Self test step failed",t);}
    }
    private static void log(String line){report.add(line);ShoreClient.LOG.warn("[selftest] {}",line);}
    private static void shot(MinecraftClient c,String name){
        ScreenshotRecorder.saveScreenshot(c.runDirectory,"selftest_"+name+".png",c.getFramebuffer(),1,t->{});
    }
    private static void finish(MinecraftClient c){
        try{Files.write(c.runDirectory.toPath().resolve("shoremod-selftest.txt"),report);}catch(Exception e){ShoreClient.LOG.error("report",e);}
        c.scheduleStop();
    }
    private static void scale(MinecraftClient c,int s){c.options.getGuiScale().setValue(s);c.onResolutionChanged();}

    // ------------------------------------------------------------------ settings screenshots
    private static void planUi(){
        enterWorld();
        step(40,c->{});
        for(int s:new int[]{2,3}){
            step(2,c->scale(c,s));
            step(4,c->{if(!(c.currentScreen instanceof ShoreScreen))c.setScreen(new ShoreScreen(null));((ShoreScreen)c.currentScreen).showGrid("All","");});
            step(12,c->shot(c,"gui"+s+"_grid"));
            step(2,c->{var sc=(ShoreScreen)c.currentScreen;sc.mouseScrolled(sc.width*0.5,sc.height*0.7,0,-30);});
            step(20,c->shot(c,"gui"+s+"_grid_scrolled"));
            step(2,c->((ShoreScreen)c.currentScreen).showGrid("HUD",""));
            step(10,c->shot(c,"gui"+s+"_hud"));
            step(2,c->((ShoreScreen)c.currentScreen).showGrid("All","zzz"));
            step(10,c->shot(c,"gui"+s+"_empty"));
            for(String id:new String[]{"zoom","crosshair","shulker","items","fps"}){
                step(2,c->((ShoreScreen)c.currentScreen).showModule(id));
                step(10,c->shot(c,"gui"+s+"_module_"+id));
            }
            step(2,c->{ShoreClient.config.crosshairMode=1;((ShoreScreen)c.currentScreen).showModule("crosshair");});
            step(10,c->shot(c,"gui"+s+"_module_crosshair_image"));
            step(2,c->{ShoreClient.config.crosshairMode=0;var sc=(ShoreScreen)c.currentScreen;sc.showModule("crosshair");sc.mouseScrolled(sc.width*0.5,sc.height*0.7,0,-30);});
            step(20,c->shot(c,"gui"+s+"_module_crosshair_scrolled"));
            for(String p:new String[]{"GENERAL","CONTROLS","UPDATES"}){
                step(2,c->((ShoreScreen)c.currentScreen).showPage(p));
                step(10,c->shot(c,"gui"+s+"_page_"+p.toLowerCase()));
            }
            step(2,c->((ShoreScreen)c.currentScreen).showGrid("All",""));
        }
        step(2,c->c.setScreen(new ClipSavedScreen(null,c.runDirectory.toPath().resolve("clips/x.avi"),28.4)));
        step(10,c->shot(c,"dialog_clip"));
        step(2,c->c.setScreen(null));
        step(20,c->shot(c,"hud_ui"));
        step(4,c->{log("UI screenshots done");finish(c);});
    }

    // ------------------------------------------------------------------ in-world inventory tests
    private static int sx,sy;
    private static double zoomSens,zoomSensWhile,zoomScaleWhile;
    private static void planWorld(){
        enterWorld();
        planInventory();
    }
    private static int deleteSlot;
    private static Slot trashSlot;
    private static void planCreative(){
        enterWorld();
        step(1,c->c.getNetworkHandler().sendChatCommand("gamemode creative"));
        await(c->c.player.getAbilities().creativeMode,20);
        step(2,c->c.setScreen(new net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen(c.player,c.world.getEnabledFeatures(),true)));
        step(10,c->{
            try{
                var tab=net.minecraft.item.ItemGroups.getGroups().stream().filter(g->g.getType()==net.minecraft.item.ItemGroup.Type.INVENTORY).findFirst().orElseThrow();
                var method=c.currentScreen.getClass().getDeclaredMethod("setSelectedTab",net.minecraft.item.ItemGroup.class);method.setAccessible(true);method.invoke(c.currentScreen,tab);
                var field=c.currentScreen.getClass().getDeclaredField("deleteItemSlot");field.setAccessible(true);trashSlot=(Slot)field.get(c.currentScreen);deleteSlot=trashSlot.id;
                sx=field(c.currentScreen,"x");sy=field(c.currentScreen,"y");
                ShoreClient.config.mouseTweaks=true;
                ((HandledScreen<?>)c.currentScreen).getScreenHandler().setCursorStack(new ItemStack(Items.STONE,16));
                moveToTrash(c);button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,0,true);
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        });
        step(2,c->button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,0,false));
        step(3,c->{log(verdict(cursor(c).isEmpty())+" creative delete X clears held items with mouse tweaks enabled");c.getNetworkHandler().sendChatCommand("give @s dirt 32");});
        await(c->c.player.getInventory().getStack(0).isOf(Items.DIRT),10);
        step(10,c->{
            try{
                // Shift-delete maps to QUICK_MOVE. Exercise that real screen action directly;
                // the preceding ordinary delete uses Minecraft's GLFW mouse callback path.
                var method=c.currentScreen.getClass().getDeclaredMethod("onMouseClick",Slot.class,int.class,int.class,SlotActionType.class);
                method.setAccessible(true);method.invoke(c.currentScreen,trashSlot,deleteSlot,0,SlotActionType.QUICK_MOVE);
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}});
        step(3,c->{log(verdict(c.player.getInventory().getStack(0).isEmpty())+" creative shift-delete clears inventory");shot(c,"creative_delete");});
        step(2,c->{log("Creative regression tests done");finish(c);});
    }
    private static void moveToTrash(MinecraftClient c){
        var window=c.getWindow();double gx=sx+trashSlot.x+8,gy=sy+trashSlot.y+8;
        invoke(c,"onCursorPos",new Class<?>[]{long.class,double.class,double.class},window.getHandle(),gx*window.getWidth()/window.getScaledWidth(),gy*window.getHeight()/window.getScaledHeight());
    }
    private static volatile int[] measuredHud;
    private static int[] hudGuiOne,hudGuiThree;
    private static float paymentUnit;
    private static void measureHud(MinecraftClient c,String name){
        measuredHud=null;
        ScreenshotRecorder.takeScreenshot(c.getFramebuffer(),image->{
            try(image){
                int[] best=null;int bestCount=-1;
                for(int base:new int[]{0,Math.max(0,image.getHeight()-120)}){
                    int minX=image.getWidth(),minY=image.getHeight(),maxX=-1,maxY=-1,count=0;
                    for(int py=base;py<Math.min(image.getHeight(),base+120);py++)for(int px=0;px<Math.min(220,image.getWidth());px++){
                        if((image.getColorArgb(px,py)&0xFFFFFF)==0x101217){count++;minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);}
                    }
                    if(count>bestCount){bestCount=count;best=new int[]{minX,minY,maxX-minX+1,maxY-minY+1};}
                }
                measuredHud=best;
                log("HUD pixels "+name+": "+Arrays.toString(measuredHud));
            }
        });
    }
    private static float paymentUiUnit(MinecraftClient c){
        try{var f=ShoreDialog.class.getDeclaredField("ui");f.setAccessible(true);return ((ShoreUi)f.get(c.currentScreen)).u;}catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void paymentButtons(MinecraftClient c){
        try{
            var f=ShoreDialog.class.getDeclaredField("hits");f.setAccessible(true);var hits=(java.util.List<?>)f.get(c.currentScreen);
            var ranges=new java.util.ArrayList<float[]>();
            for(Object hit:hits){var x=hit.getClass().getDeclaredMethod("x");var w=hit.getClass().getDeclaredMethod("w");x.setAccessible(true);w.setAccessible(true);ranges.add(new float[]{(float)x.invoke(hit),(float)w.invoke(hit)});}
            ranges.sort(java.util.Comparator.comparingDouble(v->v[0]));boolean fits=ranges.size()==4;
            for(int i=1;i<ranges.size();i++)fits&=ranges.get(i-1)[0]+ranges.get(i-1)[1]<=ranges.get(i)[0];
            if(!ranges.isEmpty())fits&=ranges.getLast()[0]+ranges.getLast()[1]-ranges.getFirst()[0]<=420-48;
            log(verdict(fits)+" payment paging and confirmation buttons fit without overlap");
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void sizeEditing(MinecraftClient c){
        try{
            var screen=(ShoreScreen)c.currentScreen;
            var uiField=ShoreScreen.class.getDeclaredField("ui");uiField.setAccessible(true);
            var ui=(ShoreUi)uiField.get(screen);
            var row=screen.slider("Size regression","",50,150,1,"%",()->ShoreClient.config.menuScale,v->ShoreClient.config.menuScale=v);
            row.trackX=100;row.trackW=100;
            var active=ShoreScreen.class.getDeclaredField("activeSlider");active.setAccessible(true);active.set(screen,row);
            var drag=new Click(125*ui.u/ui.gs,0,new MouseInput(0,0));
            screen.mouseDragged(drag,0,0);
            log(verdict(ShoreClient.config.menuScale==100)+" size drag keeps layout stable until release");
            screen.mouseReleased(drag);
            log(verdict(ShoreClient.config.menuScale==75)+" size drag applies final value on release");
            var focus=ShoreScreen.class.getDeclaredField("focused");focus.setAccessible(true);focus.set(screen,row.input);
            row.input.set("100",true);
            log(verdict(ShoreClient.config.menuScale==75)+" typed size stays draft until confirmed");
            var commit=ShoreScreen.class.getDeclaredMethod("finishInput",boolean.class);commit.setAccessible(true);commit.invoke(screen,true);
            log(verdict(ShoreClient.config.menuScale==100)+" confirmed typed size applies");
            focus.set(screen,row.input);row.input.set("5",true);commit.invoke(screen,true);
            log(verdict(ShoreClient.config.menuScale==100&&row.input.text.equals("100"))+" invalid size restores saved value");
            focus.set(screen,row.input);row.input.set("80",true);commit.invoke(screen,false);
            log(verdict(ShoreClient.config.menuScale==100&&row.input.text.equals("100"))+" canceled size preserves saved value");
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void planHud(){
        enterWorld();
        step(2,c->{
            var cfg=ShoreClient.config;cfg.hudClock=true;cfg.hudFps=cfg.hudPing=cfg.hudCoords=cfg.hudBiome=cfg.hudArmor=false;
            cfg.keystrokes=cfg.itemCounter=cfg.potionHud=cfg.targetHud=cfg.cps=cfg.toggleSprintHud=false;
            cfg.hudCorner=0;cfg.hudBgOpacity=100;cfg.hudScale=100;cfg.hudSizes.put("clock",100);scale(c,1);
        });
        step(15,c->measureHud(c,"GUI1 scale100"));await(c->measuredHud!=null,0);
        step(0,c->{hudGuiOne=measuredHud.clone();scale(c,3);});
        step(15,c->measureHud(c,"GUI3 scale100"));await(c->measuredHud!=null,0);
        step(0,c->{hudGuiThree=measuredHud.clone();log(verdict(hudGuiOne[2]>0&&Math.abs(hudGuiOne[2]-hudGuiThree[2])<=1&&Math.abs(hudGuiOne[3]-hudGuiThree[3])<=1)+" HUD physical boxes ignore Minecraft GUI zoom");ShoreClient.config.hudScale=50;});
        step(15,c->measureHud(c,"GUI3 scale50"));await(c->measuredHud!=null,0);
        step(0,c->{log(verdict(measuredHud[2]>0&&Math.abs(measuredHud[2]*2-hudGuiThree[2])<=2&&Math.abs(measuredHud[3]*2-hudGuiThree[3])<=2)+" HUD boxes halve at fifty percent");ShoreClient.config.hudScale=100;ShoreClient.config.hudSizes.put("clock",50);});
        step(15,c->measureHud(c,"GUI3 element50"));await(c->measuredHud!=null,0);
        step(0,c->{log(verdict(Math.abs(measuredHud[2]*2-hudGuiThree[2])<=2&&Math.abs(measuredHud[3]*2-hudGuiThree[3])<=2)+" HUD element setting halves its complete box");
            var users=new java.util.ArrayList<net.shoresmp.core.Wire.User>();for(int i=0;i<8;i++)users.add(new net.shoresmp.core.Wire.User(java.util.UUID.randomUUID(),"Player"+i));
            ShoreClient.config.paymentScale=100;c.setScreen(new PaymentScreen(null,net.shoresmp.core.Payments.each(users,net.shoresmp.core.Payments.amount("5b")),c.getNetworkHandler()));});
        step(10,c->{paymentUnit=paymentUiUnit(c);paymentButtons(c);ShoreClient.config.paymentScale=70;});
        step(10,c->{log(verdict(Math.abs(paymentUiUnit(c)/paymentUnit-0.7f)<0.01f)+" payment dialog geometry shrinks to seventy percent");paymentButtons(c);shot(c,"payment_compact");});
        step(2,c->{ShoreClient.config.menuScale=100;c.setScreen(new ShoreScreen(null));((ShoreScreen)c.currentScreen).showPage("GENERAL");});
        step(10,ShoreSelfTest::sizeEditing);
        planVisuals();
        planShulkerLore();
        step(2,c->{log("HUD scale regression done");finish(c);});
    }
    private static void planVisuals(){
        step(2,c->{
            c.setScreen(null);var cfg=ShoreClient.config;
            cfg.recording=true;cfg.lowOverhead=true;long before=ShoreClient.recorder.captureRequests();ShoreClient.recorder.capture();ShoreClient.recorder.capture();
            log(verdict(before==ShoreClient.recorder.captureRequests())+" performance mode makes zero framebuffer capture requests");cfg.recording=false;
            cfg.savedWaypoints.clear();cfg.waypointName="Visual test";cfg.waypointCoordinates="100 64 -100";
            log(verdict(ShoreVisuals.saveWaypoint()&&cfg.savedWaypoints.getFirst().x==100&&cfg.savedWaypoints.getFirst().z== -100)+" typed waypoint coordinates persist");
            int saved=cfg.savedWaypoints.size();cfg.waypointCoordinates="NaN 64 1";log(verdict(!ShoreVisuals.saveWaypoint()&&cfg.savedWaypoints.size()==saved)+" invalid waypoint cannot overwrite markers");cfg.waypointCoordinates="";
            cfg.blockOverlay=cfg.coloredHitboxes=cfg.totemTweaks=cfg.chatHeads=cfg.guiTweaks=cfg.betterF3=true;
            cfg.hitboxPlayersOnly=false;
            var ahead=c.player.getEyePos().add(c.player.getRotationVec(1).multiply(12));cfg.waypointName="Ahead";cfg.waypointCoordinates=(int)Math.floor(ahead.x)+" "+(int)Math.floor(ahead.y)+" "+(int)Math.floor(ahead.z);ShoreVisuals.saveWaypoint();cfg.waypointCoordinates="";
            var server=c.getServer();var id=c.player.getUuid();server.execute(()->{
                var sp=server.getPlayerManager().getPlayer(id);sp.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND,new ItemStack(Items.TOTEM_OF_UNDYING));
                var look=sp.getRotationVec(1);var pos=sp.getEyePos().add(look.multiply(3));var w=sp.getEntityWorld();
                w.setBlockState(net.minecraft.util.math.BlockPos.ofFloored(pos),Blocks.STONE.getDefaultState());
                var side=pos.add(look.z*1.5,-1,-look.x*1.5);w.spawnEntity(new net.minecraft.entity.decoration.ArmorStandEntity(w,side.x,side.y,side.z));
            });
            var message=net.minecraft.text.Text.literal("<"+c.player.getName().getString()+"> Chat heads test");
            log(verdict(ShoreChatHeads.decorate(message).getString().startsWith("   "))+" chat heads identify the sender and reserve space");
            c.inGameHud.getChatHud().addMessage(message);
        });
        step(15,c->{
            log(verdict(c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult h&&h.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK)+" block overlay renders a real targeted block");
            try{var f=ShoreVisuals.class.getDeclaredField("entities");f.setAccessible(true);log(verdict(!((java.util.List<?>)f.get(null)).isEmpty())+" hitbox overlay has a visible nearby entity");}catch(Exception e){throw new IllegalStateException(e);}
            c.gameRenderer.showFloatingItem(new ItemStack(Items.TOTEM_OF_UNDYING));});
        step(3,c->shot(c,"visual_modules"));
        step(3,c->{c.debugHudEntryList.setF3Enabled(true);});
        step(10,c->shot(c,"better_f3"));
        step(2,c->{c.debugHudEntryList.setF3Enabled(false);c.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(c.player));});
        step(10,c->{
            ShoreClient.config.inventoryOpacity=40;int tinted=ShoreVisuals.guiTint(0xFFFFFFFF);
            log(verdict(tinted==0x66FFFFFF)+" inventory texture alpha follows configured opacity");shot(c,"transparent_inventory");
        });
        step(2,c->{c.setScreen(null);log(verdict(ShoreVisuals.guiTint(0xFFFFFFFF)==0xFFFFFFFF)+" GUI tint leaves world textures unchanged");c.setScreen(new ShoreScreen(null));});
        for(String module:new String[]{"performance","blockoverlay","betterf3","guitweaks","hitboxes","totemtweaks","waypoints","chat"}){
            step(2,c->((ShoreScreen)c.currentScreen).showModule(module));step(6,c->shot(c,"settings_"+module));
        }
    }
    private static volatile boolean loreMeasured;
    private static void measureShulkerLore(MinecraftClient c,String mode){
        loreMeasured=false;
        ScreenshotRecorder.takeScreenshot(c.getFramebuffer(),image->{
            try(image){
                int green=0,purple=0;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                    int rgb=image.getColorArgb(x,y)&0xFFFFFF;
                    if(rgb==0x12AB34)green++;if(rgb==0xAB12CD)purple++;
                }
                log(verdict(green>=10&&purple>=10)+" shulker lore colors remain visible "+mode+" ("+green+", "+purple+" pixels)");
            }finally{loreMeasured=true;}
        });
    }
    private static void planShulkerLore(){
        step(2,c->{
            c.setScreen(null);var cfg=ShoreClient.config;cfg.shulkerPreview=true;cfg.shulkerMode=1;cfg.shulkerTitle=false;cfg.shulkerScale=150;
            var server=c.getServer();var id=c.player.getUuid();server.execute(()->{
                var sp=server.getPlayerManager().getPlayer(id);var box=new ItemStack(Items.PURPLE_SHULKER_BOX);
                box.set(net.minecraft.component.DataComponentTypes.LORE,new net.minecraft.component.type.LoreComponent(java.util.List.of(
                    net.minecraft.text.Text.literal("SHORE LORE MUST STAY").withColor(0x12AB34),net.minecraft.text.Text.literal("Second styled lore line").withColor(0xAB12CD))));
                box.set(net.minecraft.component.DataComponentTypes.CONTAINER,net.minecraft.component.type.ContainerComponent.fromStacks(java.util.List.of(new ItemStack(Items.DIAMOND,64))));
                sp.getInventory().setStack(9,box);
            });
        });
        step(12,c->c.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(c.player)));
        step(4,c->{var screen=(HandledScreen<?>)c.currentScreen;sx=field(screen,"x");sy=field(screen,"y");moveTo(c,9);});
        step(10,c->{shot(c,"shulker_lore_title_off");measureShulkerLore(c,"with title off at 150%");});await(c->loreMeasured,0);
        step(2,c->{ShoreClient.config.shulkerTitle=true;ShoreClient.config.shulkerScale=50;});
        step(10,c->{shot(c,"shulker_lore_small");measureShulkerLore(c,"at 50%");});await(c->loreMeasured,0);
        step(2,c->{var stack=slot(c,9).getStack();log(verdict(stack.get(net.minecraft.component.DataComponentTypes.LORE).lines().size()==2&&stack.get(net.minecraft.component.DataComponentTypes.CONTAINER).copyFirstStack().getCount()==64)+" shulker preview leaves lore and contents unchanged");});
    }
    private static void enterWorld(){
        // Wait for resource loading to finish before driving the settings UI.
        await(c->c.getOverlay()==null&&c.currentScreen!=null,200);
        step(0,c->{
            c.options.pauseOnLostFocus=false; // the test window is usually in the background
            String name="ShoreSelfTest-"+System.currentTimeMillis();
            var info=new net.minecraft.world.level.LevelInfo(name,net.minecraft.world.GameMode.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,true,
                new net.minecraft.world.rule.GameRules(net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES),net.minecraft.resource.DataConfiguration.SAFE_MODE);
            c.createIntegratedServerLoader().createAndStart(name,info,net.minecraft.world.gen.GeneratorOptions.createTestWorld(),
                net.minecraft.world.gen.WorldPresets::createTestOptions,null);
        });
        await(c->c.player!=null&&c.world!=null&&c.getServer()!=null&&c.currentScreen==null,100);
    }
    private static void planInventory(){
        step(0,c->{
            var cfg=ShoreClient.config;cfg.hudCoords=true;cfg.hudBiome=true;cfg.hudClock=true;
            cfg.mouseTweaks=true;cfg.tweakRmb=true;cfg.tweakLmb=true;cfg.tweakShift=true;cfg.tweakWheel=true;
            var server=c.getServer();var id=c.player.getUuid();
            server.execute(()->{
                var sp=server.getPlayerManager().getPlayer(id);var inv=sp.getInventory();inv.clear();
                inv.setStack(9,new ItemStack(Items.DIRT,64));          // slot 27
                inv.setStack(10,new ItemStack(Items.COBBLESTONE,64));  // slot 28
                inv.setStack(11,new ItemStack(Items.COBBLESTONE,64));  // slot 29
                inv.setStack(12,new ItemStack(Items.COBBLESTONE,32));  // slot 30
                inv.setStack(13,new ItemStack(Items.OAK_LOG,10));      // slot 31
                inv.setStack(14,new ItemStack(Items.OAK_LOG,10));      // slot 32
                inv.setStack(15,new ItemStack(Items.OAK_LOG,10));      // slot 33
                inv.setStack(18,new ItemStack(Items.SAND,64));         // slot 36
                inv.setStack(19,new ItemStack(Items.SAND,64));         // slot 37
                inv.setStack(20,new ItemStack(Items.SAND,16));         // slot 38
                inv.setStack(21,new ItemStack(Items.GRAVEL,10));       // slot 39
                inv.setStack(22,new ItemStack(Items.GRAVEL,10));       // slot 40
                inv.setStack(23,new ItemStack(Items.GRAVEL,10));       // slot 41
                inv.setStack(0,new ItemStack(Items.DIAMOND_PICKAXE));
                var pos=sp.getBlockPos().east();var world=sp.getEntityWorld();
                world.setBlockState(pos,Blocks.CHEST.getDefaultState());
                if(world.getBlockEntity(pos) instanceof ChestBlockEntity chest)sp.openHandledScreen(chest);
            });
        });
        await(c->c.currentScreen instanceof GenericContainerScreen,10);
        step(10,c->{
            var screen=(HandledScreen<?>)c.currentScreen;
            sx=field(screen,"x");sy=field(screen,"y");
            log("Chest open. All input below goes through Minecraft's real GLFW mouse callbacks.");
            pickup(c,27);
        });
        step(2,c->{});
        // ---- right-click sweep across chest slots 3, 4, 5
        step(4,c->{moveTo(c,3);button(c,GLFW.GLFW_MOUSE_BUTTON_RIGHT,0,true);});
        step(6,c->moveTo(c,4));
        step(6,c->moveTo(c,5));
        step(6,c->button(c,GLFW.GLFW_MOUSE_BUTTON_RIGHT,0,false));
        step(8,c->{
            int a=count(c,3),b=count(c,4),d=count(c,5),cur=cursor(c).getCount();
            log(verdict(a==1&&b==1&&d==1&&cur==61)+" right-click drag: chest slots 3/4/5 = "+a+"/"+b+"/"+d+" (want 1/1/1), cursor = "+cur+" (want 61)");
            pickup(c,27);
        });
        // ---- left-click collect: hold 10 logs, sweep over two more stacks of 10
        step(6,c->pickup(c,31));
        step(4,c->{moveTo(c,32);button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,0,true);});
        step(6,c->moveTo(c,33));
        step(6,c->button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,0,false));
        step(8,c->{
            int cur=cursor(c).getCount(),left=count(c,32)+count(c,33);
            log(verdict(cur==30&&left==0)+" left-click collect: cursor = "+cur+" logs (want 30), left in slots = "+left+" (want 0)");
            pickup(c,10);
        });
        // ---- shift + left sweep over the three cobblestone stacks
        step(6,c->{moveTo(c,28);button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,GLFW.GLFW_MOD_SHIFT,true);});
        step(6,c->moveTo(c,29));
        step(6,c->moveTo(c,30));
        step(6,c->button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,GLFW.GLFW_MOD_SHIFT,false));
        step(8,c->{
            int inChest=0;for(int i=0;i<27;i++)if(stack(c,i).isOf(Items.COBBLESTONE))inChest+=count(c,i);
            int left=count(c,28)+count(c,29)+count(c,30);
            log(verdict(inChest==160&&left==0)+" shift-drag: cobblestone in chest = "+inChest+" (want 160), left in inventory = "+left+" (want 0)");
        });
        // ---- scroll wheel: one log out of the chest, then one back in
        step(4,c->{moveTo(c,10);scroll(c,-1);});
        step(12,c->{
            int chest=count(c,10),inv=0;for(int i=27;i<63;i++)if(stack(c,i).isOf(Items.OAK_LOG))inv+=count(c,i);
            log(verdict(chest==29&&inv==1)+" wheel down: chest logs = "+chest+" (want 29), inventory logs = "+inv+" (want 1)");
            scroll(c,1);
        });
        step(12,c->{
            int chest=count(c,10);
            log(verdict(chest==30)+" wheel up: chest logs = "+chest+" (want 30)");
        });
        // ---- shift sweep that starts BETWEEN slots, not on one
        step(4,c->{moveToBackground(c,36);button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,GLFW.GLFW_MOD_SHIFT,true);});
        step(6,c->moveTo(c,36));
        step(6,c->moveTo(c,37));
        step(6,c->moveTo(c,38));
        step(6,c->button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,GLFW.GLFW_MOD_SHIFT,false));
        step(10,c->{
            int left=count(c,36)+count(c,37)+count(c,38),inChest=0;for(int i=0;i<27;i++)if(stack(c,i).isOf(Items.SAND))inChest+=count(c,i);
            log(verdict(left==0&&inChest==144)+" shift-drag started between slots: sand in chest = "+inChest+" (want 144), left = "+left+" (want 0)");
            pickup(c,41);
        });
        // ---- left-click collect that starts BETWEEN slots
        step(6,c->{moveToBackground(c,36);button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,0,true);});
        step(6,c->moveTo(c,39));
        step(6,c->moveTo(c,40));
        step(6,c->button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,0,false));
        step(10,c->{
            int cur=cursor(c).getCount(),left=count(c,39)+count(c,40);
            log(verdict(cur==30&&left==0)+" left-click collect started between slots: cursor = "+cur+" gravel (want 30), left = "+left+" (want 0)");
            pickup(c,41);
            shot(c,"chest");
        });
        // ---- the same sweeps in the player's own inventory screen (E)
        step(4,c->{
            c.player.closeHandledScreen(); // tells the server the chest is closed
            var server=c.getServer();var id=c.player.getUuid();
            server.execute(()->{
                var inv=server.getPlayerManager().getPlayer(id).getInventory();
                for(int i=9;i<36;i++)inv.setStack(i,ItemStack.EMPTY);
                for(int i=1;i<9;i++)inv.setStack(i,ItemStack.EMPTY);
                inv.setStack(9,new ItemStack(Items.STONE,64));inv.setStack(10,new ItemStack(Items.STONE,64)); // slots 9, 10
                inv.setStack(11,new ItemStack(Items.FLINT,8));inv.setStack(12,new ItemStack(Items.FLINT,8));   // slots 11, 12
                inv.setStack(13,new ItemStack(Items.FLINT,8));                                                  // slot 13
                var box=new ItemStack(Items.PURPLE_SHULKER_BOX);
                box.set(net.minecraft.component.DataComponentTypes.CONTAINER,net.minecraft.component.type.ContainerComponent.fromStacks(java.util.List.of(
                    new ItemStack(Items.DIAMOND,12),new ItemStack(Items.GOLDEN_APPLE,3),ItemStack.EMPTY,new ItemStack(Items.ENDER_PEARL,16),
                    ItemStack.EMPTY,ItemStack.EMPTY,new ItemStack(Items.TOTEM_OF_UNDYING),ItemStack.EMPTY,ItemStack.EMPTY,new ItemStack(Items.OBSIDIAN,64))));
                inv.setStack(17,box);                                                                           // slot 17
            });
        });
        step(10,c->{
            c.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(c.player));
        });
        step(6,c->{var screen=(HandledScreen<?>)c.currentScreen;sx=field(screen,"x");sy=field(screen,"y");
            moveTo(c,9);button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,GLFW.GLFW_MOD_SHIFT,true);});
        step(6,c->moveTo(c,10));
        step(6,c->button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,GLFW.GLFW_MOD_SHIFT,false));
        step(10,c->{
            int hotbar=0;for(int i=36;i<45;i++)if(stack(c,i).isOf(Items.STONE))hotbar+=count(c,i);
            log(verdict(hotbar==128&&count(c,9)+count(c,10)==0)+" inventory screen shift-drag: stone in hotbar = "+hotbar+" (want 128), left = "+(count(c,9)+count(c,10)));
            pickup(c,13);
        });
        step(6,c->{moveTo(c,11);button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,0,true);});
        step(6,c->moveTo(c,12));
        step(6,c->button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,0,false));
        step(10,c->{
            int cur=cursor(c).getCount();
            log(verdict(cur==24&&count(c,11)+count(c,12)==0)+" inventory screen left-click collect: cursor = "+cur+" flint (want 24), slots 11/12 = "+count(c,11)+"/"+count(c,12));
            pickup(c,13);
        });
        // ---- fast sweep: jump from the first totem straight to the last in one move
        step(4,c->{
            var server=c.getServer();var id=c.player.getUuid();
            server.execute(()->{var inv=server.getPlayerManager().getPlayer(id).getInventory();
                for(int i=1;i<9;i++)inv.setStack(i,ItemStack.EMPTY);
                for(int i=27;i<32;i++)inv.setStack(i,new ItemStack(Items.TOTEM_OF_UNDYING));}); // slots 27..31
        });
        step(10,c->{moveTo(c,27);button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,GLFW.GLFW_MOD_SHIFT,true);});
        step(6,c->moveTo(c,31));
        step(6,c->button(c,GLFW.GLFW_MOUSE_BUTTON_LEFT,GLFW.GLFW_MOD_SHIFT,false));
        step(10,c->{
            int left=0,hotbar=0;for(int i=27;i<32;i++)left+=count(c,i);
            for(int i=36;i<45;i++)if(stack(c,i).isOf(Items.TOTEM_OF_UNDYING))hotbar++;
            log(verdict(left==0&&hotbar==5)+" fast shift-sweep over 5 totems: moved to hotbar = "+hotbar+" (want 5), left = "+left+" (want 0)");
        });
        step(6,c->{var cf=ShoreClient.config;cf.shulkerMode=1;cf.shulkerScale=150;cf.shulkerBorderMode=1;moveTo(c,17);});
        step(8,c->shot(c,"shulker"));
        step(2,c->ShoreClient.config.shulkerMode=0);
        step(2,c->{
            c.player.closeHandledScreen();
            var chat=c.inGameHud.getChatHud();ShoreClient.config.filterDuplicates=true;ShoreClient.config.stackStyle=0;
            for(String m:new String[]{"Stack test A","Stack test B","Stack test A","Stack test C","Stack test A"})chat.addMessage(net.minecraft.text.Text.literal(m));
        });
        step(4,c->{
            try{
                var f=net.minecraft.client.gui.hud.ChatHud.class.getDeclaredField("messages");f.setAccessible(true);
                @SuppressWarnings("unchecked") var msgs=(java.util.List<net.minecraft.client.gui.hud.ChatHudLine>)f.get(c.inGameHud.getChatHud());
                var v=net.minecraft.client.gui.hud.ChatHud.class.getDeclaredField("visibleMessages");v.setAccessible(true);
                @SuppressWarnings("unchecked") var vis=(java.util.List<net.minecraft.client.gui.hud.ChatHudLine.Visible>)v.get(c.inGameHud.getChatHud());
                long aCount=msgs.stream().filter(m->m.content().getString().contains("Stack test A")).count();
                String newest=msgs.getFirst().content().getString(),second=msgs.get(1).content().getString(),third=msgs.get(2).content().getString();
                long groups=vis.stream().filter(net.minecraft.client.gui.hud.ChatHudLine.Visible::endOfEntry).count();
                boolean ok=aCount==1&&newest.endsWith("Stack test A [x3]")&&second.endsWith("Stack test C")&&third.endsWith("Stack test B")&&groups==msgs.size();
                log(verdict(ok)+" chat stacking: newest = \""+newest+"\", then \""+second+"\", \""+third+"\"; copies of A = "+aCount+" (want 1); visible groups "+groups+" = messages "+msgs.size());
            }catch(ReflectiveOperationException e){log("FAIL chat stacking: "+e);}
            c.setScreen(new net.minecraft.client.gui.screen.ChatScreen("",false));
        });
        step(8,c->shot(c,"chat"));
        // ---- mentions: someone else saying your name lights the line up; your own chat does not
        step(2,c->{
            ShoreClient.config.mentionHighlight=true;ShoreClient.config.mentionColor=0;
            String me=c.player.getName().getString();
            var chat=c.inGameHud.getChatHud();
            chat.addMessage(net.minecraft.text.Text.literal("<Steve> hey "+me+" want to duel?"));
            chat.addMessage(net.minecraft.text.Text.literal("<"+me+"> sure, meet at spawn"));
            chat.addMessage(net.minecraft.text.Text.literal("<Alex> nice weather today"));
        });
        step(4,c->{
            try{
                var f=net.minecraft.client.gui.hud.ChatHud.class.getDeclaredField("messages");f.setAccessible(true);
                @SuppressWarnings("unchecked") var msgs=(java.util.List<net.minecraft.client.gui.hud.ChatHudLine>)f.get(c.inGameHud.getChatHud());
                var m=net.minecraft.client.gui.hud.ChatHud.class.getDeclaredField("SHORE_MENTIONS");m.setAccessible(true);
                @SuppressWarnings("unchecked") var marked=(java.util.Set<net.minecraft.client.gui.hud.ChatHudLine>)m.get(null);
                boolean other=marked.contains(msgs.get(2)),own=marked.contains(msgs.get(1)),none=marked.contains(msgs.get(0));
                log(verdict(other&&!own&&!none)+" mentions: \""+msgs.get(2).content().getString()+"\" highlighted = "+other+" (want true); own message = "+own+" (want false); unrelated = "+none+" (want false)");
            }catch(ReflectiveOperationException e){log("FAIL mentions: "+e);}
            c.setScreen(new net.minecraft.client.gui.screen.ChatScreen("",false));
        });
        step(8,c->shot(c,"mentions"));
        // ---- PvP HUD: items, effects, and two hits on a pig
        step(2,c->{
            c.setScreen(null);
            var cfg=ShoreClient.config;cfg.keystrokes=true;cfg.targetHud=true;cfg.potionHud=true;cfg.itemCounter=true;
            var server=c.getServer();var src=server.getCommandSource();String me=c.player.getName().getString();
            server.execute(()->{
                for(String cmd:new String[]{"give "+me+" totem_of_undying 3","give "+me+" ender_pearl 16","give "+me+" golden_apple 8",
                        "effect give "+me+" speed 120 1","effect give "+me+" strength 90 0",
                        "execute at "+me+" run summon pig ^ ^ ^2 {NoAI:1b,Invulnerable:1b}"})
                    server.getCommandManager().parseAndExecute(src,cmd);
            });
        });
        step(20,c->{
            var pigs=c.world.getEntitiesByClass(net.minecraft.entity.passive.PigEntity.class,c.player.getBoundingBox().expand(6),p->true);
            if(pigs.isEmpty()){log("FAIL pvp: no pig to hit");return;}
            c.interactionManager.attackEntity(c.player,pigs.getFirst());
        });
        step(12,c->{
            var pigs=c.world.getEntitiesByClass(net.minecraft.entity.passive.PigEntity.class,c.player.getBoundingBox().expand(6),p->true);
            if(!pigs.isEmpty())c.interactionManager.attackEntity(c.player,pigs.getFirst());
        });
        step(6,c->{
            var t=ShorePvp.target();
            log(verdict(t!=null)+" target HUD: "+(t==null?"none":t.getName().getString()));
        });
        step(6,c->shot(c,"pvp_hud"));
        // ---- your own crosshair picture, and your own item in the item counter
        step(2,c->{
            try{
                var img=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g=img.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new java.awt.Color(255,60,60));g.setStroke(new java.awt.BasicStroke(3));g.drawOval(4,4,24,24);g.fillOval(14,14,4,4);g.dispose();
                var file=c.runDirectory.toPath().resolve("selftest_crosshair.png");javax.imageio.ImageIO.write(img,"png",file.toFile());
                var cf=ShoreClient.config;cf.crosshair=true;cf.crosshairMode=1;cf.crosshairImageSize=24;
                cf.crosshairImage=ShoreHud.CrosshairImage.importFile(file);
                cf.itemsCustom=new java.util.ArrayList<>(java.util.List.of("cobweb"));
                var server=c.getServer();String me=c.player.getName().getString();
                server.execute(()->server.getCommandManager().parseAndExecute(server.getCommandSource(),"give "+me+" cobweb 5"));
            }catch(Exception e){log("FAIL crosshair image setup: "+e);}
        });
        step(10,c->{
            boolean loaded=ShoreHud.CrosshairImage.texture(ShoreClient.config.crosshairImage)!=null;
            log(verdict(loaded)+" crosshair image loaded from "+ShoreClient.config.crosshairImage);
            shot(c,"crosshair_image");
            var cf=ShoreClient.config;cf.crosshairMode=0;cf.crosshairColor=1;cf.crosshairDot=true;cf.crosshairLength=6;cf.crosshairGap=3;
        });
        step(6,c->shot(c,"crosshair_shape"));
        // ---- zoom must never change the mouse sensitivity setting
        step(2,c->{zoomSens=c.options.getMouseSensitivity().getValue();ShoreClient.config.zoomEnabled=true;ShoreClient.config.zoomToggle=false;ShoreTools.zoomKey.setPressed(true);});
        step(6,c->{zoomScaleWhile=ShoreTools.lookScale();zoomSensWhile=c.options.getMouseSensitivity().getValue();ShoreTools.zoomKey.setPressed(false);});
        step(6,c->{
            double after=c.options.getMouseSensitivity().getValue();
            boolean ok=zoomScaleWhile<1&&zoomSensWhile==zoomSens&&after==zoomSens&&ShoreTools.lookScale()==1;
            log(verdict(ok)+" zoom sensitivity: setting before/while/after = "+zoomSens+"/"+zoomSensWhile+"/"+after+", turn scale while zoomed = "+String.format(Locale.ROOT,"%.2f",zoomScaleWhile)+" (want < 1), after = "+ShoreTools.lookScale());
        });
        step(2,c->{c.setScreen(null);ShoreClient.config.hudSizes.put("fps",200);ShoreClient.config.cps=true;ShoreClient.config.crosshair=true;ShoreClient.config.crosshairColor=1;ShoreClient.config.crosshairDot=true;});
        step(20,c->shot(c,"hud"));
        step(2,c->{log("World tests done");finish(c);});
    }
    private static String verdict(boolean ok){return ok?"PASS":"FAIL";}
    private static void pickup(MinecraftClient c,int id){c.interactionManager.clickSlot(((HandledScreen<?>)c.currentScreen).getScreenHandler().syncId,id,0,SlotActionType.PICKUP,c.player);}
    // Real input path: the same private Mouse callbacks GLFW invokes.
    private static void moveTo(MinecraftClient c,int slotId){
        Slot s=slot(c,slotId);var w=c.getWindow();
        double gx=sx+s.x+8,gy=sy+s.y+8;
        invoke(c,"onCursorPos",new Class<?>[]{long.class,double.class,double.class},w.getHandle(),gx*w.getWidth()/w.getScaledWidth(),gy*w.getHeight()/w.getScaledHeight());
    }
    /** A point on the inventory background just left of a slot in the first column (on no slot). */
    private static void moveToBackground(MinecraftClient c,int slotId){
        Slot s=slot(c,slotId);var w=c.getWindow();
        double gx=sx+s.x-3,gy=sy+s.y+8;
        invoke(c,"onCursorPos",new Class<?>[]{long.class,double.class,double.class},w.getHandle(),gx*w.getWidth()/w.getScaledWidth(),gy*w.getHeight()/w.getScaledHeight());
    }
    private static void button(MinecraftClient c,int button,int mods,boolean press){
        invoke(c,"onMouseButton",new Class<?>[]{long.class,MouseInput.class,int.class},c.getWindow().getHandle(),new MouseInput(button,mods),press?GLFW.GLFW_PRESS:GLFW.GLFW_RELEASE);
    }
    private static void scroll(MinecraftClient c,double amount){
        invoke(c,"onMouseScroll",new Class<?>[]{long.class,double.class,double.class},c.getWindow().getHandle(),0.0,amount);
    }
    private static void invoke(MinecraftClient c,String name,Class<?>[] types,Object... args){
        try{var m=net.minecraft.client.Mouse.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(c.mouse,args);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(name,e);}
    }
    private static Slot slot(MinecraftClient c,int id){return ((HandledScreen<?>)c.currentScreen).getScreenHandler().getSlot(id);}
    private static ItemStack stack(MinecraftClient c,int id){return slot(c,id).getStack();}
    private static int count(MinecraftClient c,int id){return stack(c,id).getCount();}
    private static ItemStack cursor(MinecraftClient c){return ((HandledScreen<?>)c.currentScreen).getScreenHandler().getCursorStack();}
    private static Click click(MinecraftClient c,int slotId,int button,int mods){
        Slot s=slot(c,slotId);return new Click(sx+s.x+8,sy+s.y+8,new MouseInput(button,mods));
    }
    private static int field(Object o,String name){
        // Dev-only (named mappings): HandledScreen keeps its origin in protected int fields x and y.
        try{var f=HandledScreen.class.getDeclaredField(name);f.setAccessible(true);return f.getInt(o);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
}



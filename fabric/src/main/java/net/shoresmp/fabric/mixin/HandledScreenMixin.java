package net.shoresmp.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.*;
import net.minecraft.text.Text;
import net.shoresmp.fabric.Config;
import net.shoresmp.fabric.ShoreClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;

/**
 * Mouse Tweaks-style inventory controls, done entirely with ordinary slot clicks:
 *  - Right-click drag with a stack: drop one item into every slot you sweep over (again on revisits).
 *  - Left-click drag with a stack, starting on a matching stack: collect every matching stack you sweep over.
 *    (Starting on an empty slot keeps vanilla's even-split drag.)
 *  - Shift + left-click drag: quick-move every stack you sweep over, with or without a held item.
 *  - Scroll over an item: down sends one item to the other inventory, up pulls one matching item back.
 *  - Right-click a shulker box with an empty cursor to preview it.
 * Held buttons are read from Minecraft's own mouse state each frame as well as from drag events.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow @Final protected ScreenHandler handler;
    @Shadow private Slot getSlotAt(double x,double y){throw new AssertionError();}
    @Unique private static final int SHORE_NONE=0,SHORE_PLACE=1,SHORE_MOVE=2,SHORE_COLLECT=3;
    @Shadow protected Slot focusedSlot;
    @Shadow protected abstract void onMouseClick(Slot slot,int slotId,int button,SlotActionType type);
    @Unique private static final boolean SHORE_EXTERNAL_TWEAKS=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mousetweaks");
    @Unique private boolean shore$special(Slot slot){return (Object)this instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen&&(slot==null||!(slot.inventory instanceof PlayerInventory));}
    @Unique private int shore$mode=SHORE_NONE;
    @Unique private Slot shore$lastSlot;
    @Unique private final Set<Slot> shore$visited=new HashSet<>();
    @Unique private ItemStack shore$filter=ItemStack.EMPTY;
    @Unique private double shore$lx,shore$ly;
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected abstract boolean isClickOutsideBounds(double mouseX,double mouseY,int left,int top);
    protected HandledScreenMixin(Text title){super(title);}

    @Unique private static Config shore$cfg(){return ShoreClient.config;}
    @Unique private static boolean shore$on(boolean sub){Config c=shore$cfg();return !SHORE_EXTERNAL_TWEAKS&&c!=null&&c.mouseTweaks&&sub;}
    @Unique private void shore$click(Slot slot,int button,SlotActionType type){
        var client=MinecraftClient.getInstance();
        if(client.player==null||client.interactionManager==null)return;
        onMouseClick(slot,slot.id,button,type); // Preserve the screen's own creative/special-slot handling.
    }

    // ------------------------------------------------------------------ press
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void shore$press(Click click,boolean doubled,CallbackInfoReturnable<Boolean> cir){
        double mx=click.x(),my=click.y();int button=click.button();
        Config c=shore$cfg();
        Slot slot=getSlotAt(mx,my);
        if(shore$special(slot)){shore$reset();return;}
        if(c==null)return;
        ItemStack cursor=handler.getCursorStack();
        // A sweep may start anywhere inside the inventory window, not only on a slot. Presses outside
        // the window keep vanilla behaviour (e.g. throwing the held stack).
        boolean inside=slot!=null||!isClickOutsideBounds(mx,my,x,y);
        shore$visited.clear();shore$lastSlot=slot;shore$lx=mx;shore$ly=my;
        shore$filter=cursor.isEmpty()?ItemStack.EMPTY:cursor.copy();
        boolean shift=click.hasShift();
        shore$mode=SHORE_NONE;
        if(!inside)return;
        if(button==1&&!shift&&!cursor.isEmpty()&&shore$on(c.tweakRmb)){
            shore$mode=SHORE_PLACE;if(slot!=null)shore$place(slot);cir.setReturnValue(true);
        }else if(button==0&&shift&&shore$on(c.tweakShift)){
            shore$mode=SHORE_MOVE;
            if(slot!=null){
                shore$visited.add(slot);
                // On a slot, vanilla performs this first shift-click itself (keeps shift+double-click "move all").
                if(!shore$matches(slot)){cir.setReturnValue(true);shore$visited.remove(slot);}
            }else cir.setReturnValue(true);
        }else if(button==0&&!shift&&!cursor.isEmpty()&&shore$on(c.tweakLmb)){
            if(slot==null){shore$mode=SHORE_COLLECT;cir.setReturnValue(true);}
            else if(slot.hasStack()&&ItemStack.areItemsAndComponentsEqual(slot.getStack(),cursor)){
                shore$mode=SHORE_COLLECT;shore$visited.add(slot);shore$collect(slot);cir.setReturnValue(true);
            } // pressing on an empty or different slot keeps vanilla's place / even-split drag
        }
    }
    /** With an item on the cursor, shift-sweeps only move stacks of that item. */
    @Unique private boolean shore$matches(Slot slot){
        return slot.hasStack()&&(shore$filter.isEmpty()||ItemStack.areItemsAndComponentsEqual(slot.getStack(),shore$filter));
    }

    // ------------------------------------------------------------------ drag
    @Inject(method="mouseDragged",at=@At("HEAD"),cancellable=true)
    private void shore$drag(Click click,double dx,double dy,CallbackInfoReturnable<Boolean> cir){
        if(shore$mode==SHORE_NONE)return;
        shore$step(click.x(),click.y());
        cir.setReturnValue(true);
    }
    // Minecraft does not record a button as held when a screen consumes the press (Mouse returns
    // early), so the sweep stays active until our own release handler sees the button go up.
    // Polling the cursor each frame catches fast sweeps that skip drag events.
    // renderMain, not render: recipe-book screens (inventory, crafting table, furnace) override
    // render() without calling it, but every handled screen draws through renderMain.
    @Inject(method="renderMain",at=@At("HEAD"))
    private void shore$poll(DrawContext ctx,int mx,int my,float delta,CallbackInfo ci){
        if(shore$mode==SHORE_NONE)return;
        var mouse=MinecraftClient.getInstance().mouse;var window=MinecraftClient.getInstance().getWindow();
        shore$step(mouse.getScaledX(window),mouse.getScaledY(window));
    }
    @Inject(method="removed",at=@At("HEAD"),require=0)
    private void shore$closed(CallbackInfo ci){shore$reset();}
    @Inject(method="mouseReleased",at=@At("HEAD"),cancellable=true)
    private void shore$release(Click click,CallbackInfoReturnable<Boolean> cir){
        int button=click.button();
        if((shore$mode==SHORE_PLACE&&button==1)||(shore$mode==SHORE_COLLECT&&button==0)){shore$reset();cir.setReturnValue(true);}
        else if(shore$mode==SHORE_MOVE&&button==0)shore$reset(); // vanilla still handles double-click move-all
    }
    @Unique private void shore$reset(){shore$mode=SHORE_NONE;shore$lastSlot=null;shore$visited.clear();}
    /**
     * Follows the cursor's path since the last update in small steps, so a fast sweep still visits
     * every slot it crosses instead of jumping over some of them.
     */
    @Unique private void shore$step(double mx,double my){
        double dx=mx-shore$lx,dy=my-shore$ly;
        if(dx==0&&dy==0)return;
        int n=Math.min(256,Math.max(1,(int)Math.ceil(Math.hypot(dx,dy)/3)));
        for(int i=1;i<=n&&shore$mode!=SHORE_NONE;i++)shore$visit(getSlotAt(shore$lx+dx*i/n,shore$ly+dy*i/n));
        shore$lx=mx;shore$ly=my;
    }
    @Unique private void shore$visit(Slot slot){
        if(slot==shore$lastSlot)return;
        shore$lastSlot=slot;
        if(slot==null||shore$special(slot))return;
        switch(shore$mode){
            case SHORE_PLACE->shore$place(slot);
            case SHORE_MOVE->{if(shore$matches(slot)&&shore$visited.add(slot))shore$quickMove(slot);}
            case SHORE_COLLECT->{if(shore$visited.add(slot))shore$collect(slot);}
            default->{}
        }
    }

    // ------------------------------------------------------------------ actions
    @Unique private void shore$place(Slot slot){
        var client=MinecraftClient.getInstance();var cursor=handler.getCursorStack();
        if(client.player==null||cursor.isEmpty()||!slot.isEnabled()||!slot.canInsert(cursor))return;
        var stack=slot.getStack();
        if(!stack.isEmpty()&&(!ItemStack.areItemsAndComponentsEqual(stack,cursor)||stack.getCount()>=slot.getMaxItemCount(cursor)))return;
        shore$click(slot,1,SlotActionType.PICKUP);
    }
    @Unique private void shore$quickMove(Slot slot){
        var client=MinecraftClient.getInstance();
        if(client.player==null||!slot.isEnabled()||!slot.canTakeItems(client.player))return;
        shore$click(slot,0,SlotActionType.QUICK_MOVE);
    }
    /** Pull a matching stack into the cursor: merge the cursor into the slot, then pick the combined stack up. */
    @Unique private void shore$collect(Slot slot){
        var client=MinecraftClient.getInstance();var cursor=handler.getCursorStack();var stack=slot.getStack();
        if(client.player==null||cursor.isEmpty()||stack.isEmpty()||!slot.isEnabled()||!slot.canTakeItems(client.player))return;
        if(!ItemStack.areItemsAndComponentsEqual(stack,cursor))return;
        if(cursor.getCount()+stack.getCount()>Math.min(cursor.getMaxCount(),slot.getMaxItemCount(cursor)))return; // would not fit in one stack
        if(!slot.canInsert(cursor))return;
        shore$click(slot,0,SlotActionType.PICKUP);
        shore$click(slot,0,SlotActionType.PICKUP);
    }

    // ------------------------------------------------------------------ wheel
    @Inject(method="mouseScrolled",at=@At("HEAD"),cancellable=true)
    private void shore$wheel(double mx,double my,double horizontal,double vertical,CallbackInfoReturnable<Boolean> cir){
        Config c=shore$cfg();
        if((Object)this instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen)return;
        if(vertical==0||!shore$on(c!=null&&c.tweakWheel)||!handler.getCursorStack().isEmpty())return;
        Slot slot=getSlotAt(mx,my);
        if(slot==null||!slot.isEnabled())return;
        boolean done=vertical<0?shore$pushOne(slot):shore$pullOne(slot);
        if(done||slot.hasStack())cir.setReturnValue(true);
    }
    @Unique private List<Slot> shore$otherSide(Slot from){
        var out=new ArrayList<Slot>();
        boolean playerOnly=handler instanceof PlayerScreenHandler;
        boolean fromHotbar=from.inventory instanceof PlayerInventory&&from.getIndex()<9;
        for(Slot s:handler.slots){
            if(s==from||!s.isEnabled()||s instanceof CraftingResultSlot)continue;
            if(playerOnly){
                // Inventory screen: move between the hotbar and the main inventory.
                if(!(s.inventory instanceof PlayerInventory)||s.getIndex()>=36)continue;
                if(fromHotbar==(s.getIndex()<9))continue;
            }else if(s.inventory==from.inventory)continue;
            out.add(s);
        }
        return out;
    }
    @Unique private boolean shore$pushOne(Slot from){
        var client=MinecraftClient.getInstance();var stack=from.getStack();
        if(stack.isEmpty()||client.player==null||!from.canTakeItems(client.player))return false;
        Slot target=null;
        for(Slot s:shore$otherSide(from))if(s.hasStack()&&ItemStack.areItemsAndComponentsEqual(s.getStack(),stack)&&s.getStack().getCount()<s.getMaxItemCount(stack)&&s.canInsert(stack)){target=s;break;}
        if(target==null)for(Slot s:shore$otherSide(from))if(!s.hasStack()&&s.canInsert(stack)){target=s;break;}
        if(target==null)return false;
        shore$click(from,0,SlotActionType.PICKUP);   // pick up the stack
        shore$click(target,1,SlotActionType.PICKUP); // drop one
        shore$click(from,0,SlotActionType.PICKUP);   // put the rest back
        return true;
    }
    @Unique private boolean shore$pullOne(Slot into){
        var client=MinecraftClient.getInstance();var here=into.getStack();
        if(here.isEmpty()||client.player==null||here.getCount()>=into.getMaxItemCount(here))return false;
        Slot source=null;
        for(Slot s:shore$otherSide(into))if(s.hasStack()&&ItemStack.areItemsAndComponentsEqual(s.getStack(),here)&&s.canTakeItems(client.player)){source=s;break;}
        if(source==null)return false;
        shore$click(source,0,SlotActionType.PICKUP);
        shore$click(into,1,SlotActionType.PICKUP);
        shore$click(source,0,SlotActionType.PICKUP);
        return true;
    }

    @Unique private boolean shore$reportedLore;
    // ------------------------------------------------------------------ shulker preview
    /** Hovering a shulker box (holding Shift, or always) shows its normal tooltip text above the 27-slot preview. */
    @Inject(method="drawMouseoverTooltip",at=@At("HEAD"),cancellable=true)
    private void shore$shulkerTooltip(DrawContext ctx,int mx,int my,CallbackInfo ci){
        Config c=shore$cfg();
        if(c==null||!c.shulkerPreview||focusedSlot==null||!handler.getCursorStack().isEmpty())return;
        ItemStack stack=focusedSlot.getStack();
        if(!(stack.getItem() instanceof BlockItem block)||!(block.getBlock() instanceof ShulkerBoxBlock box))return;
        if(c.shulkerMode==0){
            long w=MinecraftClient.getInstance().getWindow().getHandle();
            if(GLFW.glfwGetKey(w,GLFW.GLFW_KEY_LEFT_SHIFT)!=GLFW.GLFW_PRESS&&GLFW.glfwGetKey(w,GLFW.GLFW_KEY_RIGHT_SHIFT)!=GLFW.GLFW_PRESS)return;
        }
        ci.cancel();
        var items=net.minecraft.util.collection.DefaultedList.ofSize(27,ItemStack.EMPTY);
        stack.getOrDefault(DataComponentTypes.CONTAINER,ContainerComponent.DEFAULT).copyTo(items);
        // Keep the complete vanilla/mod-provided tooltip, including styled lore.
        // The title toggle hides only the name; it must never hide lore or attributes.
        float sc=c.shulkerScale/100f;
        var normal=Screen.getTooltipFromItem(MinecraftClient.getInstance(),stack);
        var lines=new ArrayList<net.minecraft.text.OrderedText>();
        int maxTextWidth=Math.max(40,(int)((width-12)/sc)-12);
        for(int i=c.shulkerTitle?0:Math.min(1,normal.size());i<normal.size();i++)
            lines.addAll(textRenderer.wrapLines(normal.get(i),maxTextWidth));
        int lineH=textRenderer.fontHeight+1,textH=lines.isEmpty()?0:lines.size()*lineH+4;
        int pw=9*18+10;
        for(var line:lines)pw=Math.max(pw,textRenderer.getWidth(line)+12);
        int ph=3*18+10+textH;
        sc=Math.min(sc,Math.min((width-8)/(float)pw,(height-8)/(float)ph));
        float sw=pw*sc,sh=ph*sc;
        float x=mx+12,y=my-12;
        if(x+sw>width-4)x=mx-12-sw;
        x=Math.clamp(x,4,Math.max(4,width-sw-4));
        y=Math.clamp(y,4,Math.max(4,height-sh-4));
        if(!shore$reportedLore&&"hud".equals(System.getProperty("shoremod.selftest"))){
            var lore=stack.get(DataComponentTypes.LORE);
            if(lore!=null&&!lore.lines().isEmpty()&&lore.lines().getFirst().getString().equals("SHORE LORE MUST STAY")){
                shore$reportedLore=true;ShoreClient.LOG.info("SHORE_SYNTHETIC_PREVIEW lines={} rect={},{},{},{} scale={}",lines.size(),x,y,sw,sh,sc);
            }
        }
        ctx.createNewRootLayer();
        var dye=box.getColor();
        int border=switch(c.shulkerBorderMode){
            case 0->dye!=null?0xFF000000|dye.getEntityColor():0xFF8E5CC0; // undyed shulkers are purple
            case 1->0xFF000000|c.accent;
            default->0;
        };
        int bg=(Math.round(c.shulkerBgOpacity*2.55f)<<24)|0x101216;
        var m=ctx.getMatrices();m.pushMatrix();m.translate(x,y);m.scale(sc,sc);
        if(border!=0){ctx.fill(1,0,pw-1,ph,border);ctx.fill(0,1,pw,ph-1,border);}
        ctx.fill(2,1,pw-2,ph-1,bg);ctx.fill(1,2,pw-1,ph-2,bg);
        for(int i=0;i<lines.size();i++)ctx.drawTextWithShadow(textRenderer,lines.get(i),6,5+i*lineH,0xFFFFFFFF);
        for(int i=0;i<27;i++){
            int sx=(pw-9*18)/2+i%9*18,sy=5+textH+i/9*18;
            if(c.shulkerEmptySlots)ctx.fill(sx,sy,sx+17,sy+17,0x22FFFFFF);
            ItemStack item=items.get(i);
            if(!item.isEmpty()){ctx.drawItem(item,sx,sy);ctx.drawStackOverlay(textRenderer,item,sx,sy);}
        }
        m.popMatrix();
    }
}

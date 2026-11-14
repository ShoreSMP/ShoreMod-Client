package net.shoresmp.fabric;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/**
 * PvP information read from what the client already sees:
 * the player you are fighting, and totem pops of nearby players. Nothing here changes how you play.
 */
public final class ShorePvp {
    private ShorePvp(){}
    private static LivingEntity target;
    private static long targetAt;
    private static final Map<UUID,Integer> pops=new HashMap<>();

    public static void register(){
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(world.isClient()&&player==MinecraftClient.getInstance().player)onAttack(player,entity);
            return ActionResult.PASS;
        });
    }
    private static void onAttack(PlayerEntity player,Entity entity){
        long now=System.currentTimeMillis();
        if(entity instanceof LivingEntity living){target=living;targetAt=now;}
    }
    public static void tick(MinecraftClient client){
        var player=client.player;
        if(player==null){target=null;pops.clear();return;}
        if(target!=null&&(target.isRemoved()||!target.isAlive()||System.currentTimeMillis()-targetAt>5000))target=null;
    }

    public static LivingEntity target(){return target;}

    /** Called from the entity-status packet on the main thread; status 35 is a totem of undying going off. */
    public static void onEntityStatus(Entity entity,byte status){
        Config c=ShoreClient.config;var client=MinecraftClient.getInstance();
        if(c==null||!(entity instanceof PlayerEntity p))return;
        if(status==35){
            int n=pops.merge(p.getUuid(),1,Integer::sum);
            if(c.totemPops&&(p!=client.player||c.totemPopsSelf))
                ShoreClient.tell(p.getName().getString()+" popped "+n+(n==1?" totem":" totems"));
        }else if(status==3){ // death
            Integer n=pops.remove(p.getUuid());
            if(c.totemPops&&n!=null&&p!=client.player)ShoreClient.tell(p.getName().getString()+" died after popping "+n+(n==1?" totem":" totems"));
        }
    }
}


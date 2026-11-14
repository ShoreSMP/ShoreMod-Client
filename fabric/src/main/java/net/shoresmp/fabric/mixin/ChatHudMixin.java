package net.shoresmp.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.*;
import net.shoresmp.fabric.ShoreClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow private int scrolledLines;
    // Increase both stored messages and wrapped visible lines so scrolling reaches the retained history.
    @ModifyConstant(method={"addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V","addVisibleMessage"},constant=@Constant(intValue=100))
    private int shore$historyLimit(int vanilla){
        if(ShoreClient.config==null)return 5000;
        return ShoreClient.config.extendedChat?ShoreClient.config.chatHistoryLimit:vanilla;
    }
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;
    /** Plain text and repeat count of each line we added, so a repeat can find and merge with it. */
    @Unique private final Map<ChatHudLine,String> shore$keys=new WeakHashMap<>();
    @Unique private final Map<ChatHudLine,Integer> shore$counts=new WeakHashMap<>();
    @Unique private String shore$pendingKey;
    @Unique private boolean shore$pendingMention;
    /** Chat lines that mention the player, and their wrapped screen lines (filled with the highlight color). */
    @Unique private static final Set<ChatHudLine> SHORE_MENTIONS=Collections.newSetFromMap(new WeakHashMap<>());
    @Unique private static final Set<ChatHudLine.Visible> SHORE_MENTION_LINES=Collections.newSetFromMap(new WeakHashMap<>());
    @Unique private static final int[] SHORE_MENTION_COLORS={0xF5C542,-1,0xFF5C5C,0x5CD65C,0x5C9DFF};

    /** Whole-word match of the player's name or a highlight word, ignoring the player's own sender prefix. */
    @Unique private static boolean shore$mentions(String plain){
        var config=ShoreClient.config;var player=MinecraftClient.getInstance().player;
        List<String> words=new ArrayList<>();
        if(player!=null)words.add(player.getName().getString());
        for(String w:config.highlightWords)if(w!=null&&!w.isBlank())words.add(w.strip());
        String self=player==null?null:player.getName().getString();
        for(String w:words){
            var m=java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])"+java.util.regex.Pattern.quote(w)+"(?![A-Za-z0-9_])",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(plain);
            while(m.find()){
                // Your own name as the sender ("<name> hi", "[Rank] name: hi") or as the subject at the start
                // of a server message ("name has made the advancement", "name joined") is not a mention.
                String after=plain.substring(m.end()).stripLeading();
                String before=plain.substring(0,m.start());
                boolean own=w.equalsIgnoreCase(self);
                boolean sender=own&&m.start()<=24&&!after.isEmpty()&&":>]»".indexOf(after.charAt(0))>=0;
                boolean subject=own&&before.replaceAll("\\[[^\\]]*\\]|[^A-Za-z0-9_]","").isEmpty();
                if(!sender&&!subject)return true;
            }
        }
        return false;
    }
    @Inject(method="addVisibleMessage",at=@At("TAIL"))
    private void shore$markMention(ChatHudLine line,CallbackInfo ci){
        if(shore$pendingMention){SHORE_MENTIONS.add(line);shore$pendingMention=false;}
        if(!SHORE_MENTIONS.contains(line)||visibleMessages.isEmpty())return;
        // The newest message's wrapped lines sit at the front, ending with the first endOfEntry line.
        for(int i=0;i<visibleMessages.size();i++){
            SHORE_MENTION_LINES.add(visibleMessages.get(i));
            if(i+1>=visibleMessages.size()||visibleMessages.get(i+1).endOfEntry())break;
        }
    }
    /** Replaces the dark background behind a mention line with the highlight color. */
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="method_75802",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/hud/ChatHud$Backend;fill(IIIII)V"))
    private static void shore$highlight(ChatHud.Backend backend,int x1,int y1,int x2,int y2,int color,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) ChatHudLine.Visible line,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly=true,ordinal=1) float fade){
        var config=ShoreClient.config;
        if(config==null||!config.mentionHighlight||!SHORE_MENTION_LINES.contains(line)){original.call(backend,x1,y1,x2,y2,color);return;}
        int rgb=config.mentionColor==5?config.mentionCustomColor:config.mentionColor==1?config.accent:SHORE_MENTION_COLORS[config.mentionColor];
        int alpha=Math.round(Math.clamp(fade,0f,1f)*0x70);
        original.call(backend,x1,y1,x2,y2,(alpha<<24)|rgb);
        original.call(backend,x1,y1,x1+2,y2,(Math.round(Math.clamp(fade,0f,1f)*255)<<24)|rgb);
    }
    @Unique private int shore$count=1;

    /**
     * Message stacking: if the same text (ignoring formatting) appeared within the last few messages,
     * that earlier copy is removed and the new one is shown at the bottom with an increased count.
     * Only the old message's own wrapped lines are removed, so the rest of the history is untouched.
     */
    @Inject(method="addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",at=@At("HEAD"),order=900)
    private void shore$duplicates(Text message,net.minecraft.network.message.MessageSignatureData signature,net.minecraft.client.gui.hud.MessageIndicator indicator,CallbackInfo ci){
        shore$count=1;shore$pendingKey=null;
        var config=ShoreClient.config;
        if(config==null)return;
        String key=message.getString().strip();
        shore$pendingKey=key;
        shore$pendingMention=config.mentionHighlight&&shore$mentions(key);
        if(!config.filterDuplicates||key.isEmpty())return;
        int group=0; // start of the current message's lines in visibleMessages (newest first)
        for(int i=0;i<Math.min(config.stackLookback,messages.size());i++){
            ChatHudLine line=messages.get(i);
            int len=0;
            while(group+len<visibleMessages.size()){len++;if(group+len>=visibleMessages.size()||visibleMessages.get(group+len).endOfEntry())break;}
            if(key.equals(shore$keys.get(line))&&Objects.equals(indicator,line.indicator())){
                shore$count=Math.min(999_999,shore$counts.getOrDefault(line,1)+1);
                messages.remove(i);
                for(int k=0;k<len&&group<visibleMessages.size();k++)visibleMessages.remove(group);
                if(scrolledLines>0)scrolledLines=Math.max(0,scrolledLines-len);
                return;
            }
            group+=len;
        }
    }
    @Inject(method="addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",at=@At("TAIL"))
    private void shore$rememberLine(Text message,net.minecraft.network.message.MessageSignatureData signature,net.minecraft.client.gui.hud.MessageIndicator indicator,CallbackInfo ci){
        if(messages.isEmpty()||shore$pendingKey==null)return;
        ChatHudLine line=messages.getFirst();
        shore$keys.put(line,shore$pendingKey);shore$counts.put(line,shore$count);
    }
    @ModifyVariable(method="addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",at=@At("HEAD"),argsOnly=true,ordinal=0,order=1100)
    private Text shore$decorate(Text message){
        if(ShoreClient.config==null)return message;
        var config=ShoreClient.config;var result=Text.empty();String plain=message.getString().toLowerCase(Locale.ROOT);
        if(config.timestamps){
            String pattern=(config.timestamps24?"HH:mm":"h:mm")+(config.timestampSeconds?":ss":"")+(config.timestamps24?"":" a");
            result.append(Text.literal("["+LocalTime.now().format(DateTimeFormatter.ofPattern(pattern,Locale.ROOT))+"] ").withColor(0x829AAE));
        }
        boolean highlight=shore$pendingMention;
        // shore$count is already updated by shore$duplicates (order 900); only ping for the first copy.
        if(highlight&&config.mentionSound&&shore$count==1)
            MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING,1.6f));
        // Keep original styles, links and hover events intact.
        result.append(message);
        if(config.filterDuplicates && shore$count>1)
            result.append(Text.literal(switch(config.stackStyle){case 1->" ("+shore$count+")";case 2->" x"+shore$count;default->" [x"+shore$count+"]";}).setStyle(Style.EMPTY.withColor(0xAAAAAA)));
        return result;
    }
}


"""Post-mapping API moves shared by unobfuscated Minecraft 26.x."""
from pathlib import Path
import re
for p in src.rglob('*.java'):
 s=p.read_text(encoding='utf-8')
 s=s.replace('net.fabricmc.fabric.api.client.gui.screen().v1','net.fabricmc.fabric.api.client.screen.v1')
 s=s.replace('ClientCommandManager','ClientCommands')
 s=s.replace('net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper','net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper')
 s=s.replace('KeyBindingHelper.registerKeyBinding','KeyMappingHelper.registerKeyMapping')
 s=s.replace('PayloadTypeRegistry.playC2S()','PayloadTypeRegistry.serverboundPlay()').replace('PayloadTypeRegistry.playS2C()','PayloadTypeRegistry.clientboundPlay()')
 s=s.replace('Screens.getButtons(', 'Screens.getWidgets(')
 s=s.replace('net.minecraft.client.GuiMessage','net.minecraft.client.multiplayer.chat.GuiMessage').replace('net/minecraft/client/GuiMessage','net/minecraft/client/multiplayer/chat/GuiMessage')
 s=re.sub(r'\bClickType\b','ContainerInput',s)
 s=s.replace('.drawCenteredString(','.centeredText(').replace('.drawString(','.text(')
 s=s.replace('.renderItemDecorations(','.itemDecorations(').replace('.renderItem(','.item(')
 s=s.replace('vanilla.render(ctx,tick)','vanilla.extractRenderState(ctx,tick)')
 s=s.replace('.getChat().addMessage(','.getChat().addClientSystemMessage(')
 s=s.replace('.handleInventoryMouseClick(','.handleContainerInput(')
 if target in ['26.2','26.3']:
  s=s.replace('client.hud.', 'client.gui.hud.').replace('client.gui.setOverlayMessage(', 'client.gui.hud.setOverlayMessage(')
  s=s.replace('client.options.hideGui','client.gui.hud.isHidden()')
  s=s.replace('client.getMainRenderTarget()', 'client.gameRenderer.mainRenderTarget()')
  s=s.replace('minecraft.levelRenderer.allChanged()', 'minecraft.levelRenderer.invalidateCompiledGeometry(minecraft.level,minecraft.options,minecraft.gameRenderer.mainCamera(),minecraft.getBlockColors())')
  s=s.replace('client.levelRenderer.allChanged()', 'client.levelRenderer.invalidateCompiledGeometry(client.level,client.options,client.gameRenderer.mainCamera(),client.getBlockColors())')
  s=s.replace('mc.levelRenderer.allChanged()', 'mc.levelRenderer.invalidateCompiledGeometry(mc.level,mc.options,mc.gameRenderer.mainCamera(),mc.getBlockColors())')
 if target=='26.3':
  s=s.replace('com.mojang.blaze3d.textures.', 'com.mojang.renderpearl.api.textures.')
  s=s.replace('import org.lwjgl.glfw.GLFW;','')
  s=re.sub(r'(?:org.lwjgl.glfw.)?GLFW.GLFW_KEY_(\w+)',lambda m:'com.mojang.blaze3d.platform.InputConstants.KEY_'+{'RIGHT_SHIFT':'RSHIFT','LEFT_SHIFT':'LSHIFT','LEFT_CONTROL':'LCONTROL','RIGHT_CONTROL':'RCONTROL','ENTER':'RETURN','KP_ENTER':'NUMPADENTER'}.get(m[1],m[1]),s)
  s=s.replace('GLFW.GLFW_MOD_CONTROL','org.lwjgl.sdl.SDLKeycode.SDL_KMOD_CTRL')
  s=s.replace('InputConstants.Type.KEYSYM','InputConstants.Type.KEYBOARD')
  s=s.replace('InputConstants.isKeyDown(client.getWindow(),','InputConstants.isKeyDown(')
  s=s.replace('input.scancode()', 'input.keycode()')
  s=s.replace('input.key()==256','input.key()==com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE')
  s=s.replace('org.lwjgl.glfw.GLFW.glfwGetMouseButton(window,key.getValue())==org.lwjgl.glfw.GLFW.GLFW_PRESS','(org.lwjgl.sdl.SDLMouse.SDL_GetMouseState((java.nio.FloatBuffer)null,(java.nio.FloatBuffer)null)&(1<<(key.getValue()-1)))!=0')
  s=re.sub(r'GLFW.glfwGetKey\(w,([^)]*)\)!=GLFW.GLFW_PRESS',r'!com.mojang.blaze3d.platform.InputConstants.isKeyDown(\1)',s)
  s=s.replace('net.minecraft.util.Util.getPlatform().openUri(', 'com.mojang.blaze3d.Blaze3D.openUri(').replace('net.minecraft.util.Util.getPlatform().openPath(', 'com.mojang.blaze3d.Blaze3D.openPath(')
  s=s.replace('Util.getPlatform().openUri(', 'com.mojang.blaze3d.Blaze3D.openUri(').replace('Util.getPlatform().openPath(', 'com.mojang.blaze3d.Blaze3D.openPath(')
  if p.name=='ShoreScreen.java':
   s=s.replace('click.button()!=0','click.button()!=org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT')
   s=s.replace('click.button()>=2','(click.button()==org.lwjgl.sdl.SDLMouse.SDL_BUTTON_MIDDLE||click.button()>org.lwjgl.sdl.SDLMouse.SDL_BUTTON_RIGHT)')
   s=s.replace('public void tick(){','public void tick(){minecraft.onTextInputFocusChange(this,focused!=null);')
   s=s.replace('public void onClose(){','public void onClose(){minecraft.onTextInputFocusChange(this,false);')
   s=s.replace('    @Override public void onClose(){','    @Override public void removed(){minecraft.onTextInputFocusChange(this,false);super.removed();}\n    @Override public void onClose(){')
   start=s.index('    void chooseCrosshairImage(){')
   end=s.index('    private Row listening;',start)
   s=s[:start]+"""    void chooseCrosshairImage(){
        java.awt.EventQueue.invokeLater(()->{
            try{
                var dialog=new java.awt.FileDialog((java.awt.Frame)null,"Choose a PNG or JPEG crosshair",java.awt.FileDialog.LOAD);
                dialog.setFilenameFilter((directory,name)->name.toLowerCase(java.util.Locale.ROOT).matches(".*\\\\.(png|jpe?g)"));
                dialog.setVisible(true);
                var chosen=dialog.getFile()==null?null:java.nio.file.Path.of(dialog.getDirectory(),dialog.getFile());dialog.dispose();
                if(chosen!=null)importCrosshair(chosen);
            }catch(Exception e){ShoreClient.tellError("Drop a PNG or JPEG onto this crosshair settings page instead.");}
        });
    }
"""+s[end:]
  if p.name=='CrosshairEditorScreen.java':
   s=s.replace('int button=click.button();','int button=switch(click.button()){case 1->0;case 3->1;case 2->2;default->click.button();};')
   s=s.replace(' @Override public void onClose(){',' @Override public void tick(){minecraft.onTextInputFocusChange(this,colorFocus);}\n @Override public void removed(){minecraft.onTextInputFocusChange(this,false);super.removed();}\n @Override public void onClose(){minecraft.onTextInputFocusChange(this,false);')
  if p.name=='HandledScreenMixin.java':
   s=s.replace('int button=click.button();','int button=switch(click.button()){case 1->0;case 3->1;case 2->2;default->click.button();};')
  if p.name=='ShoreTools.java':
   s=s.replace('if(button==0)leftClicks.addLast(now);else if(button==1)rightClicks.addLast(now);','if(button==1)leftClicks.addLast(now);else if(button==3)rightClicks.addLast(now);')
 if p.name=='BrightnessOptionsMixin.java':
  s=s.replace('method="write"','method="save"')
 if p.name=='ChatHudMixin.java':
  s=s.replace('Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V','Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V')
  s=s.replace('net.minecraft.client.multiplayer.chat.GuiMessageTag indicator,CallbackInfo ci','net.minecraft.client.multiplayer.chat.GuiMessageSource source,net.minecraft.client.multiplayer.chat.GuiMessageTag indicator,CallbackInfo ci')
 if p.name=='HandledScreenMixin.java':
  s=s.replace('method="renderContents"','method="extractContents"').replace('method="renderTooltip"','method="extractTooltip"')
 p.write_text(s,encoding='utf-8')


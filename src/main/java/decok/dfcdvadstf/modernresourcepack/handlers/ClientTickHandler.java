package decok.dfcdvadstf.modernresourcepack.handlers;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenResourcePacks;

public class ClientTickHandler {
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // 检查当前是否在资源包界面并处理拖放
            if (Minecraft.getMinecraft().currentScreen instanceof GuiScreenResourcePacks) {
                ResourcePackDragHandler.handleDragAndDrop((GuiScreenResourcePacks) Minecraft.getMinecraft().currentScreen);
            }
        }
    }
}
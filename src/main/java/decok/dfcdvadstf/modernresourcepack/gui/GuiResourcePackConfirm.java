package decok.dfcdvadstf.modernresourcepack.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.gui.GuiScreen;

public class GuiResourcePackConfirm extends GuiYesNo implements GuiYesNoCallback {
    
    private final GuiScreen parentScreen;
    private final String[] resourcePackNames;
    
    public GuiResourcePackConfirm(GuiScreen parent, String[] packNames, String title, String message) {
        super((GuiYesNoCallback) parent, title, message + "\n" + String.join("\n", packNames), 0);
        this.parentScreen = parent;
        this.resourcePackNames = packNames;
    }
    
    @Override
    public void confirmClicked(boolean result, int id) {
        // 处理确认结果
        this.mc.displayGuiScreen(this.parentScreen);
    }
}
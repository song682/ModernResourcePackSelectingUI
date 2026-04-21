package decok.dfcdvadstf.modernresourcepack.mixins;

import decok.dfcdvadstf.modernresourcepack.handlers.ResourcePackDropHandler;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Mixin(GuiScreenResourcePacks.class)
public abstract class GuiScreenResourcePacksMixin extends GuiScreen implements GuiYesNoCallback {

    @Unique
    private File[] modernresourcepack$pendingFiles;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void onInitGui(CallbackInfo ci) {
        ResourcePackDropHandler.register();
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void onDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.mc.currentScreen != this) return;

        String[] dropped = ResourcePackDropHandler.pollPendingFiles();
        if (dropped != null && dropped.length > 0) {
            modernresourcepack$pendingFiles = new File[dropped.length];
            for (int i = 0; i < dropped.length; i++) {
                modernresourcepack$pendingFiles[i] = new File(dropped[i]);
            }

            String fileName = modernresourcepack$pendingFiles[0].getName();
            if (modernresourcepack$pendingFiles.length > 1) {
                fileName += " 等" + modernresourcepack$pendingFiles.length + "个文件";
            }

            GuiYesNo confirm = new GuiYesNo(
                    this,
                    "你确认要加入这个资源包吗？",
                    fileName,
                    "是",
                    "否",
                    0
            );
            this.mc.displayGuiScreen(confirm);
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        this.mc.displayGuiScreen(this);

        if (result && id == 0 && modernresourcepack$pendingFiles != null) {
            File targetDir = this.mc.getResourcePackRepository().getDirResourcepacks();
            for (File file : modernresourcepack$pendingFiles) {
                try {
                    File target = new File(targetDir, file.getName());
                    Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            this.mc.getResourcePackRepository().updateRepositoryEntriesAll();
            this.initGui();
            modernresourcepack$pendingFiles = null;
        }
    }
}

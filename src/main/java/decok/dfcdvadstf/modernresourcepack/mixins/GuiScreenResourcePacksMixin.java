package decok.dfcdvadstf.modernresourcepack.mixins;

import decok.dfcdvadstf.modernresourcepack.handlers.ResourcePackDropHandler;
import net.minecraft.client.gui.GuiResourcePackAvailable;
import net.minecraft.client.gui.GuiResourcePackSelected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Mixin(GuiScreenResourcePacks.class)
public abstract class GuiScreenResourcePacksMixin extends GuiScreen implements GuiYesNoCallback {

    @Shadow private GuiResourcePackAvailable field_146970_i;
    @Shadow private GuiResourcePackSelected field_146967_r;
    @Shadow private List field_146966_g;
    @Shadow private List field_146969_h;

    @Unique
    private File[] modernresourcepack$pendingFiles;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void onInitGui(CallbackInfo ci) {
        ResourcePackDropHandler.register();
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void onDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.mc.currentScreen != this) return;

        this.drawCenteredString(this.fontRendererObj, I18n.format("gui.resourcepacks.drag"), this.width / 2, 28, 0x888888);

        String[] dropped = ResourcePackDropHandler.pollPendingFiles();
        if (dropped != null && dropped.length > 0) {
            modernresourcepack$pendingFiles = new File[dropped.length];
            for (int i = 0; i < dropped.length; i++) {
                modernresourcepack$pendingFiles[i] = new File(dropped[i]);
            }

            String fileName = modernresourcepack$pendingFiles[0].getName();
            if (modernresourcepack$pendingFiles.length > 1) {
                fileName += " and " + modernresourcepack$pendingFiles.length + " more files";
            }

            GuiYesNo confirm = new GuiYesNo(this, I18n.format("resourcepack.install.confirm.title"), fileName, I18n.format("gui.yes"), I18n.format("gui.no"), 0);
            this.mc.displayGuiScreen(confirm);
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        this.mc.displayGuiScreen(this);

        if (result && id == 0 && modernresourcepack$pendingFiles != null) {
            File targetDir = this.mc.getResourcePackRepository().getDirResourcepacks();
            List<String> copiedNames = new ArrayList<>();

            for (File file : modernresourcepack$pendingFiles) {
                try {
                    File target = new File(targetDir, file.getName());
                    Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    copiedNames.add(file.getName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            ResourcePackRepository repo = this.mc.getResourcePackRepository();
            repo.updateRepositoryEntriesAll();

            for (String copiedName : copiedNames) {
                List allEntries = repo.getRepositoryEntriesAll();
                for (Object obj : allEntries) {
                    ResourcePackRepository.Entry entry = (ResourcePackRepository.Entry) obj;
                    if (entry.getResourcePackName().equals(copiedName.replace(".zip", ""))) {
                        List<ResourcePackRepository.Entry> selected = new ArrayList<>(repo.getRepositoryEntries());
                        selected.add(entry);
                        repo.func_148527_a(selected);
                        break;
                    }
                }
            }

            this.initGui();
            modernresourcepack$pendingFiles = null;
        }
    }
}

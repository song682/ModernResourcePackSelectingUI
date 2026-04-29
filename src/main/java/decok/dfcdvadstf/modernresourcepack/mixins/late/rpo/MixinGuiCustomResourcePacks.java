package decok.dfcdvadstf.modernresourcepack.mixins.late.rpo;

import chylex.respack.gui.GuiCustomResourcePacks;
import decok.dfcdvadstf.modernresourcepack.handlers.ResourcePackDropHandler;
import decok.dfcdvadstf.modernresourcepack.utils.IncompatiblePackHelper;
import decok.dfcdvadstf.modernresourcepack.utils.VirtualPackEntryFactory;
import net.minecraft.client.gui.GuiResourcePackAvailable;
import net.minecraft.client.gui.GuiResourcePackSelected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Late mixin — applies only when Resource Pack Organizer is present.
 * <p>
 * RPO swaps vanilla {@code GuiScreenResourcePacks} for its own
 * {@code GuiCustomResourcePacks} via {@code GuiOpenEvent}, and overrides
 * {@code initGui}/{@code drawScreen}/{@code onGuiClosed} without calling super,
 * so our vanilla mixins never fire. This mixin re-introduces all our
 * enhancements directly on RPO's GUI and manipulates RPO's own pack lists.
 */
@Mixin(GuiCustomResourcePacks.class)
public abstract class MixinGuiCustomResourcePacks extends GuiScreen implements GuiYesNoCallback {

    @Shadow(remap = false) private List<ResourcePackListEntryFound> listPacksAvailable;
    @Shadow(remap = false) private List<ResourcePackListEntryFound> listPacksSelected;
    @Shadow(remap = false) private GuiResourcePackAvailable guiPacksAvailable;
    @Shadow(remap = false) private GuiResourcePackSelected guiPacksSelected;
    @Shadow(remap = false) private File currentFolder;

    @Unique
    private File[] modernresourcepack$pendingFiles;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void modernresourcepack$onInitGui(CallbackInfo ci) {
        ResourcePackDropHandler.register();
        // Safety net: clear stale pending entry left by Esc-cancelled GuiYesNo
        IncompatiblePackHelper.clearPendingEntry();

        // Push both lists down so they don't collide with the title + drag hint
        // (RPO hardcodes top = 4 in its initGui, matching vanilla's 32 -> 45 bump)
        if (this.guiPacksAvailable != null) this.guiPacksAvailable.top = 45;
        if (this.guiPacksSelected != null) this.guiPacksSelected.top = 45;
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void modernresourcepack$onDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.mc.currentScreen != this) return;

        // Vanilla title that RPO dropped — put it back at the top
        this.drawCenteredString(this.fontRendererObj, I18n.format("resourcePack.title"), this.width / 2, 16, 0xFFFFFF);
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

    @Inject(method = "onGuiClosed", at = @At("RETURN"))
    private void modernresourcepack$onGuiClosed(CallbackInfo ci) {
        ResourcePackDropHandler.unregister();
    }

    /**
     * RPO replaces the repository with its own subclass that overrides
     * {@code updateRepositoryEntriesAll} and never calls super, so our
     * {@link decok.dfcdvadstf.modernresourcepack.mixins.MixinResourcePackRepository}
     * never runs here. {@code createAvailablePackList} also ignores
     * {@code getRepositoryEntriesAll()} entirely — it only lists files on disk.
     * So we build the virtual Entry ourselves (via the shared factory) and append it.
     */
    @Inject(method = "createAvailablePackList", at = @At("RETURN"), remap = false)
    private void modernresourcepack$appendVirtualPacks(ResourcePackRepository repository,
                                                        CallbackInfoReturnable<List<ResourcePackListEntryFound>> cir) {
        // Only append at the root — in a subfolder a phantom entry would be confusing
        if (this.currentFolder == null || !this.currentFolder.equals(repository.getDirResourcepacks())) return;

        List<ResourcePackListEntryFound> list = cir.getReturnValue();
        if (list == null) return;

        ResourcePackRepository.Entry virtualEntry = VirtualPackEntryFactory.getOrCreate(repository);
        if (virtualEntry == null) return;

        // Already enabled? Then it's on the selected side — don't duplicate.
        if (repository.getRepositoryEntries().contains(virtualEntry)) return;
        // Already in the available list (paranoia dedup)?
        for (ResourcePackListEntryFound e : list) {
            if (e.func_148318_i() == virtualEntry) return;
        }

        list.add(new ResourcePackListEntryFound((GuiScreenResourcePacks) (Object) this, virtualEntry));
    }

    /**
     * Added as a new method on GuiCustomResourcePacks; overrides the
     * confirmClicked inherited from GuiScreenResourcePacks (our vanilla mixin).
     * Mutates RPO's own lists instead of the unused vanilla fields.
     */
    @Override
    public void confirmClicked(boolean result, int id) {
        // Incompatible pack confirmation (id == 1)
        // Semantics: match vanilla ▶ button — only move entry between GUI lists,
        // NOT modify repo. Repo only gets written when user clicks "Done".
        if (id == 1) {
            ResourcePackListEntry pendingEntry = IncompatiblePackHelper.getPendingEntry();
            IncompatiblePackHelper.clearPendingEntry();
            this.mc.displayGuiScreen(this); // triggers initGui which rebuilds RPO lists

            if (result && pendingEntry instanceof ResourcePackListEntryFound) {
                ResourcePackRepository.Entry targetRepoEntry =
                        ((ResourcePackListEntryFound) pendingEntry).func_148318_i();

                ResourcePackListEntryFound movedEntry = null;
                for (ResourcePackListEntryFound e : this.listPacksAvailable) {
                    if (e.func_148318_i() == targetRepoEntry) {
                        movedEntry = e;
                        break;
                    }
                }
                if (movedEntry != null) {
                    this.listPacksAvailable.remove(movedEntry);
                    this.listPacksSelected.add(0, movedEntry);
                }
            }
            return;
        }

        this.mc.displayGuiScreen(this);

        // Drag-drop install confirmation (id == 0)
        if (result && id == 0 && modernresourcepack$pendingFiles != null) {
            File targetDir = this.mc.getResourcePackRepository().getDirResourcepacks();
            List<String> copiedNames = new ArrayList<String>();

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
                        List<ResourcePackRepository.Entry> selected = new ArrayList<ResourcePackRepository.Entry>(repo.getRepositoryEntries());
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

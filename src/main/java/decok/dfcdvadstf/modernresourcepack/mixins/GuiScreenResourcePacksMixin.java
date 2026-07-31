package decok.dfcdvadstf.modernresourcepack.mixins;

import decok.dfcdvadstf.modernresourcepack.gui.WorldResourcePackListEntry;
import decok.dfcdvadstf.modernresourcepack.utils.IncompatiblePackHelper;
import decok.dfcdvadstf.modernresourcepack.utils.WorldPackEntryFactory;
import decok.dfcdvadstf.modernresourcepack.utils.handlers.ResourcePackDropHandler;
import decok.dfcdvadstf.modernresourcepack.api.WorldResourcePackManager;
import net.minecraft.client.gui.GuiButton;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
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
        // Safety net: clear any stale pending entry left by Esc-cancelled GuiYesNo
        // (GuiYesNo's default keyTyped closes without calling confirmClicked)
        IncompatiblePackHelper.clearPendingEntry();

        // Pin the world-scoped pack to the top of the selected list. This is UI only —
        // actual loading is handled by MinecraftMixin; the entry here is for visibility
        // and to make it clear the pack exists and can't be moved.
        modernrpui$injectWorldPackEntry();
    }

    @Unique
    @SuppressWarnings("unchecked")
    private void modernrpui$injectWorldPackEntry() {
        if (!WorldResourcePackManager.isActive()) return;
        // Dedup — initGui can fire multiple times (e.g. after a confirm dialog closes)
        for (Object o : this.field_146969_h) {
            if (o instanceof WorldResourcePackListEntry) return;
        }
        ResourcePackRepository repo = this.mc.getResourcePackRepository();
        ResourcePackRepository.Entry fake = WorldPackEntryFactory.createForActivePack(repo);
        if (fake == null) return;

        WorldResourcePackListEntry entry =
                new WorldResourcePackListEntry((GuiScreenResourcePacks) (Object) this, fake);
        // field_146969_h is display order (reversed from load order) — index 0 = top visual = highest priority
        this.field_146969_h.add(0, entry);
    }

    /**
     * Strip world-pack entries out of the selected list before vanilla writes its contents
     * into {@code gameSettings.resourcePacks}. Otherwise the internal id leaks into options.txt
     * and sticks around after the user leaves the world.
     *
     * Only runs for the Done button (id == 1). Open-folder (id == 2) must leave the list alone
     * so the entry is still visible when the user comes back.
     */
    @Inject(method = "actionPerformed", at = @At("HEAD"))
    private void modernrpui$filterWorldPackBeforeDone(GuiButton button, CallbackInfo ci) {
        if (button == null || !button.enabled || button.id != 1) return;
        Iterator<?> it = this.field_146969_h.iterator();
        while (it.hasNext()) {
            if (it.next() instanceof WorldResourcePackListEntry) {
                it.remove();
            }
        }
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
        // Incompatible pack confirmation (id == 1)
        // Semantics: match vanilla ▶ button — only move entry between GUI lists,
        // NOT modify repo. Repo only gets written when user clicks "Done".
        // This way, if user Esc’s out of the resource pack screen without clicking Done,
        // the pack won’t actually load and will be back in available list next time.
        if (id == 1) {
            ResourcePackListEntry pendingEntry = IncompatiblePackHelper.getPendingEntry();
            IncompatiblePackHelper.clearPendingEntry();
            this.mc.displayGuiScreen(this); // triggers initGui which rebuilds lists

            if (result && pendingEntry instanceof ResourcePackListEntryFound) {
                ResourcePackRepository.Entry targetRepoEntry =
                        ((ResourcePackListEntryFound) pendingEntry).func_148318_i();
                // Locate the rebuilt entry in available list by matching underlying repo entry
                ResourcePackListEntry movedEntry = null;
                for (Object o : this.field_146966_g) {
                    if (o instanceof ResourcePackListEntryFound
                            && ((ResourcePackListEntryFound) o).func_148318_i() == targetRepoEntry) {
                        movedEntry = (ResourcePackListEntry) o;
                        break;
                    }
                }
                if (movedEntry != null) {
                    this.field_146966_g.remove(movedEntry);
                    this.field_146969_h.add(0, movedEntry);
                }
            }
            return;
        }

        this.mc.displayGuiScreen(this);

        // Drag-drop install confirmation (id == 0)
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

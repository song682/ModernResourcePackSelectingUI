package decok.dfcdvadstf.modernresourcepack.mixinplugin;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import cpw.mods.fml.relauncher.FMLLaunchHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@LateMixin
public class ModernResourcePackSelectingUILateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.modernresourceselectui.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();

        // Late mixins are client-only for this mod (GUI patches)
        if (!FMLLaunchHandler.side().isClient()) {
            return mixins;
        }

        // Resource Pack Organizer compatibility — it replaces GuiScreenResourcePacks
        // with its own GuiCustomResourcePacks, breaking our vanilla injections.
        if (loadedMods.contains("ResourcePackOrganizer")) {
            mixins.add("rpo.MixinGuiCustomResourcePacks");
        }

        return mixins;
    }
}

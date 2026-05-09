package decok.dfcdvadstf.modernresourcepack.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.modernresourcepack.api.WorldResourcePack;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.EnumChatFormatting;

/**
 * UI row for the world-scoped resource pack. Visually lives in the "selected" list at
 * the very top (highest load priority).
 *
 * Why extend {@link ResourcePackListEntryFound} instead of a plain
 * {@link net.minecraft.client.resources.ResourcePackListEntry}? Because RPO's
 * {@code listPacksSelected} is typed as {@code List<ResourcePackListEntryFound>} — a
 * bare entry would never fit into it. Extending Found gives us a single class that
 * both vanilla and RPO lists accept.
 *
 * We lock all interaction by overriding {@link #func_148310_d()} to return {@code false}.
 * The base {@code mousePressed} gates every button (add/remove/up/down) on that flag, so
 * one override kills them all — no separate click interception needed.
 */
@SideOnly(Side.CLIENT)
public class WorldResourcePackListEntry extends ResourcePackListEntryFound {

    public WorldResourcePackListEntry(GuiScreenResourcePacks parent, ResourcePackRepository.Entry entry) {
        super(parent, entry);
    }

    @Override
    protected String func_148312_b() {
        // Pack display name — localized with a safe hardcoded fallback
        String label = I18n.format(WorldResourcePack.DISPLAY_NAME_KEY);
        if (label == null || label.isEmpty() || WorldResourcePack.DISPLAY_NAME_KEY.equals(label)) {
            label = WorldResourcePack.DISPLAY_NAME_FALLBACK;
        }
        return EnumChatFormatting.GOLD + label;
    }

    @Override
    protected String func_148311_a() {
        // Description line shown below the name — hint that it's save-bound + locked.
        String desc = I18n.format("resourcepack.world.description");
        if (desc == null || desc.isEmpty() || "resourcepack.world.description".equals(desc)) {
            desc = "\u6b64\u5305\u7531\u5f53\u524d\u5b58\u6863\u6307\u5b9a\uff0c\u65e0\u6cd5\u8c03\u6574";
        }
        return EnumChatFormatting.GRAY + desc;
    }

    /**
     * Returning {@code false} disables every interactive branch in
     * {@link net.minecraft.client.resources.ResourcePackListEntry#mousePressed} — add
     * button, remove button, and both reorder arrows. The entry becomes inert.
     */
    @Override
    protected boolean func_148310_d() {
        return false;
    }
}

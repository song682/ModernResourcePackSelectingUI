package decok.dfcdvadstf.modernresourcepack.api;

import java.io.File;

import net.minecraft.client.resources.FileResourcePack;

/**
 * World-scoped resource pack — backed by a real {@code resources.zip} that lives inside
 * a save folder. Everything about it is vanilla {@link FileResourcePack} behavior, we just
 * hard-override the display name so it shows up as "世界指定资源包" wherever Minecraft
 * queries {@code getPackName()} (repository registry, logs, error traces, etc.).
 *
 * Well, quite simple — we don't want the filename "resources.zip" leaking into the UI.
 */
public class WorldResourcePack extends FileResourcePack {

    /** Internal marker name — never shown, but used anywhere that needs a stable id. */
    public static final String INTERNAL_ID = "__modernrpui_world_resources__";

    /** Display name key — fallback is hardcoded so the pack still renders if the lang file is missing. */
    public static final String DISPLAY_NAME_KEY = "resourcepack.world.name";
    public static final String DISPLAY_NAME_FALLBACK = "\u4e16\u754c\u6307\u5b9a\u8d44\u6e90\u5305";

    public WorldResourcePack(File zipFile) {
        super(zipFile);
    }

    @Override
    public String getPackName() {
        // Stable id — used by ResourcePackRepository.Entry.getResourcePackName() for dedup.
        // We do NOT return the localized label here; that's the UI entry's job.
        return INTERNAL_ID;
    }
}

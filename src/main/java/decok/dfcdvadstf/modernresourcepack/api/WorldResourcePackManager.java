package decok.dfcdvadstf.modernresourcepack.api;

import java.io.File;
import java.io.IOException;

import net.minecraft.client.Minecraft;

/**
 * Central state holder for the world-scoped resource pack.
 *
 * Lifecycle: set on client connect (when we detect a {@code resources.zip} in the save
 * folder), cleared on disconnect. Queried by {@code refreshResources} mixin to decide
 * whether to append this pack to the top of the loading list, and by the resource pack
 * UI mixins to inject a locked entry into the selected list.
 *
 * Not thread-safe — Minecraft client is single-threaded for these touch points.
 */
public final class WorldResourcePackManager {

    public static final String WORLD_PACK_FILENAME = "resources.zip";

    private static WorldResourcePack activePack;
    private static File activePackFile;

    private WorldResourcePackManager() {}

    public static boolean isActive() {
        return activePack != null;
    }

    public static WorldResourcePack getActivePack() {
        return activePack;
    }

    public static File getActivePackFile() {
        return activePackFile;
    }

    /**
     * Activate the world pack from a save folder. Does nothing if the folder has no
     * {@code resources.zip} — that's the signal "this world didn't opt in".
     *
     * @return {@code true} if a pack was activated (caller should {@code refreshResources}).
     */
    public static boolean activateFromSaveFolder(File saveFolder) {
        if (saveFolder == null) return false;
        File candidate = new File(saveFolder, WORLD_PACK_FILENAME);
        if (!candidate.isFile()) return false;

        // Idempotency — same file, same active pack, no-op
        if (activePackFile != null && activePackFile.equals(candidate) && activePack != null) {
            return false;
        }

        // Switching worlds without a clean disconnect? Tear down the previous one first.
        closeActivePackQuietly();

        activePack = new WorldResourcePack(candidate);
        activePackFile = candidate;
        return true;
    }

    /**
     * Drop the active pack. Safe to call even when nothing's active.
     *
     * @return {@code true} if a pack was actually cleared.
     */
    public static boolean deactivate() {
        if (activePack == null) return false;
        closeActivePackQuietly();
        activePack = null;
        activePackFile = null;
        return true;
    }

    /** Trigger a client-side resource reload on the next safe moment. */
    public static void scheduleRefresh() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.scheduleResourcesRefresh();
        }
    }

    private static void closeActivePackQuietly() {
        if (activePack == null) return;
        try {
            activePack.close();
        } catch (IOException ignored) {
            // zip handle release is best-effort — nothing useful to do on failure
        }
    }
}

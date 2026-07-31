package decok.dfcdvadstf.modernresourcepack.utils;

import net.minecraft.client.resources.ResourcePackListEntry;

public class IncompatiblePackHelper {

    private static ResourcePackListEntry pendingEntry;

    public static void setPendingEntry(ResourcePackListEntry entry) {
        pendingEntry = entry;
    }

    public static ResourcePackListEntry getPendingEntry() {
        return pendingEntry;
    }

    public static void clearPendingEntry() {
        pendingEntry = null;
    }
}

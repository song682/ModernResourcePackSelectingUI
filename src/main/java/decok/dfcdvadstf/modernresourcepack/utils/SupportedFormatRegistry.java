package decok.dfcdvadstf.modernresourcepack.utils;

import net.minecraft.client.resources.data.PackMetadataSection;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Stores the optional {@code supported_format} ([min, max]) declared in a
 * pack.mcmeta, keyed by the {@link PackMetadataSection} instance parsed for it.
 *
 * The registry is consulted by the incompatibility logic: when {@code min == 1}
 * the pack is considered compatible with 1.7.10 regardless of its pack_format.
 */
public final class SupportedFormatRegistry {

    private static final Map<PackMetadataSection, int[]> MAP =
            new WeakHashMap<PackMetadataSection, int[]>();

    private SupportedFormatRegistry() {}

    public static synchronized void register(PackMetadataSection section, int min, int max) {
        if (section == null) return;
        MAP.put(section, new int[] { min, max });
    }

    /** Returns {@code [min, max]} or {@code null} when the pack didn't declare one. */
    public static synchronized int[] get(PackMetadataSection section) {
        if (section == null) return null;
        return MAP.get(section);
    }
}

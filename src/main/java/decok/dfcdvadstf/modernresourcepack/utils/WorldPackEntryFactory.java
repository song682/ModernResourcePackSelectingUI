package decok.dfcdvadstf.modernresourcepack.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;

import decok.dfcdvadstf.modernresourcepack.mixins.ResourcePackEntryAccessor;
import decok.dfcdvadstf.modernresourcepack.api.WorldResourcePack;
import decok.dfcdvadstf.modernresourcepack.api.WorldResourcePackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.data.PackMetadataSection;

/**
 * Builds the fake {@link ResourcePackRepository.Entry} that the UI needs so our world pack
 * can ride in the same list as regular packs.
 *
 * Unlike the high-contrast factory this one is NOT cached — the backing pack can swap out
 * across worlds, and a stale Entry pointing at a closed zip would crash the moment the UI
 * tries to render it.
 */
public final class WorldPackEntryFactory {

    private static Constructor<ResourcePackRepository.Entry> entryCtor;

    private WorldPackEntryFactory() {}

    /**
     * @return a fresh Entry bound to the currently active world pack, or {@code null}
     *         if no world pack is active or construction failed.
     */
    public static ResourcePackRepository.Entry createForActivePack(ResourcePackRepository repository) {
        WorldResourcePack pack = WorldResourcePackManager.getActivePack();
        if (pack == null || repository == null) return null;

        try {
            if (entryCtor == null) {
                Constructor<ResourcePackRepository.Entry> ctor = ResourcePackRepository.Entry.class
                        .getDeclaredConstructor(ResourcePackRepository.class, File.class);
                ctor.setAccessible(true);
                entryCtor = ctor;
            }
            // File arg just satisfies the vanilla constructor — we overwrite the pack field directly.
            File placeholder = WorldResourcePackManager.getActivePackFile();
            if (placeholder == null) placeholder = new File(WorldResourcePack.INTERNAL_ID);

            ResourcePackRepository.Entry entry = entryCtor.newInstance(repository, placeholder);

            ResourcePackEntryAccessor access = (ResourcePackEntryAccessor) entry;
            access.setReResourcePack(pack);

            // Metadata is best-effort — if resources.zip has no pack.mcmeta, vanilla would normally
            // reject the pack. We keep it loadable anyway and just skip the metadata section.
            try {
                PackMetadataSection metadata = (PackMetadataSection)
                        pack.getPackMetadata(repository.rprMetadataSerializer, "pack");
                access.setRePackMetadataSection(metadata);
            } catch (Exception ignored) {
                access.setRePackMetadataSection(null);
            }

            access.setTexturePackIcon(loadIcon(pack));

            return entry;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static BufferedImage loadIcon(WorldResourcePack pack) {
        try {
            return pack.getPackImage();
        } catch (IOException e) {
            try {
                return Minecraft.getMinecraft().mcDefaultResourcePack.getPackImage();
            } catch (IOException fallback) {
                return null;
            }
        }
    }
}

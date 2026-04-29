package decok.dfcdvadstf.modernresourcepack.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;

import decok.dfcdvadstf.modernresourcepack.mixins.ResourcePackEntryAccessor;
import decok.dfcdvadstf.modernresourcepack.resource.HighContrastPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.data.PackMetadataSection;

/**
 * Shared factory for our in-jar virtual pack Entry. One instance, one Entry, reused
 * across vanilla and RPO paths — so list.contains(...) identity checks line up.
 */
public final class VirtualPackEntryFactory {

    private static HighContrastPack pack;
    private static ResourcePackRepository.Entry cachedEntry;
    private static Constructor<ResourcePackRepository.Entry> entryCtor;

    private VirtualPackEntryFactory() {}

    public static HighContrastPack getPack() {
        if (pack == null) pack = new HighContrastPack();
        return pack;
    }

    /**
     * Returns the singleton virtual Entry, constructing it against the given repository
     * the first time. Subsequent calls return the same instance regardless of repo.
     * That's fine — Entry never reads its outer back after construction for our use case.
     */
    public static ResourcePackRepository.Entry getOrCreate(ResourcePackRepository repository) {
        if (cachedEntry != null) return cachedEntry;
        try {
            if (entryCtor == null) {
                Constructor<ResourcePackRepository.Entry> ctor = ResourcePackRepository.Entry.class
                    .getDeclaredConstructor(ResourcePackRepository.class, File.class);
                ctor.setAccessible(true);
                entryCtor = ctor;
            }
            File placeholder = new File(HighContrastPack.VIRTUAL_ID);
            ResourcePackRepository.Entry entry = entryCtor.newInstance(repository, placeholder);

            HighContrastPack hcp = getPack();
            ResourcePackEntryAccessor access = (ResourcePackEntryAccessor) entry;
            access.setReResourcePack(hcp);

            PackMetadataSection metadata = (PackMetadataSection)
                hcp.getPackMetadata(repository.rprMetadataSerializer, "pack");
            access.setRePackMetadataSection(metadata);
            access.setTexturePackIcon(loadIcon(repository));

            cachedEntry = entry;
            return cachedEntry;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static BufferedImage loadIcon(ResourcePackRepository repository) {
        try {
            return getPack().getPackImage();
        } catch (IOException e) {
            try {
                return Minecraft.getMinecraft().mcDefaultResourcePack.getPackImage();
            } catch (IOException fallback) {
                return null;
            }
        }
    }
}

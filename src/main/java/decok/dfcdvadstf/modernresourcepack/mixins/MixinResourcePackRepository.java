package decok.dfcdvadstf.modernresourcepack.mixins;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.List;

import decok.dfcdvadstf.modernresourcepack.resource.HighContrastPack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.resources.data.PackMetadataSection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends our built-in virtual pack ({@link HighContrastPack}) to the vanilla repository's
 * "all packs" list at the end of {@code updateRepositoryEntriesAll}. The pack is fully
 * in-memory — no files ever land in {@code resourcepacks/}, so there's nothing for users
 * to accidentally redistribute.
 *
 * {@link ResourcePackRepository.Entry}'s constructor is package-private for an inner class,
 * so we reach for it via a plain {@link Class#getDeclaredConstructor} call. That's standard
 * Java reflection — four lines, not a 50-line constructor scanner.
 */
@Mixin(ResourcePackRepository.class)
public abstract class MixinResourcePackRepository {

    @Shadow
    private List repositoryEntriesAll;

    @Shadow
    public IMetadataSerializer rprMetadataSerializer;

    @Shadow
    public IResourcePack rprDefaultResourcePack;

    @Unique
    private static HighContrastPack modernrpui$builtinPack;

    @Unique
    private static Constructor<ResourcePackRepository.Entry> modernrpui$entryCtor;

    @SuppressWarnings("unchecked")
    @Inject(method = "updateRepositoryEntriesAll", at = @At("RETURN"))
    private void modernrpui$appendBuiltinPacks(CallbackInfo ci) {
        // Dedup — vanilla may call this more than once during a session
        for (Object raw : this.repositoryEntriesAll) {
            ResourcePackRepository.Entry existing = (ResourcePackRepository.Entry) raw;
            IResourcePack rp = existing.getResourcePack();
            if (rp != null && HighContrastPack.DISPLAY_NAME.equals(rp.getPackName())) {
                return;
            }
        }

        ResourcePackRepository.Entry entry = modernrpui$buildEntry();
        if (entry != null) {
            this.repositoryEntriesAll.add(entry);
        }
    }

    @Unique
    private ResourcePackRepository.Entry modernrpui$buildEntry() {
        try {
            if (modernrpui$builtinPack == null) {
                modernrpui$builtinPack = new HighContrastPack();
            }

            ResourcePackRepository self = (ResourcePackRepository) (Object) this;
            ResourcePackRepository.Entry entry = modernrpui$instantiateEntry(self);
            if (entry == null) return null;

            ResourcePackEntryAccessor access = (ResourcePackEntryAccessor) entry;
            access.setReResourcePack(modernrpui$builtinPack);

            PackMetadataSection metadata = (PackMetadataSection) modernrpui$builtinPack
                .getPackMetadata(this.rprMetadataSerializer, "pack");
            access.setRePackMetadataSection(metadata);
            access.setTexturePackIcon(modernrpui$loadIcon());

            return entry;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Unique
    private ResourcePackRepository.Entry modernrpui$instantiateEntry(ResourcePackRepository self) throws Exception {
        // Entry is a non-static inner class, so its JVM constructor is (Outer, File).
        // Dummy file path — vanilla never reads it since we populate the fields directly.
        File placeholder = new File(HighContrastPack.VIRTUAL_ID);
        if (modernrpui$entryCtor == null) {
            Constructor<ResourcePackRepository.Entry> ctor = ResourcePackRepository.Entry.class
                .getDeclaredConstructor(ResourcePackRepository.class, File.class);
            ctor.setAccessible(true);
            modernrpui$entryCtor = ctor;
        }
        return modernrpui$entryCtor.newInstance(self, placeholder);
    }

    @Unique
    private BufferedImage modernrpui$loadIcon() {
        try {
            return modernrpui$builtinPack.getPackImage();
        } catch (IOException e) {
            try {
                return this.rprDefaultResourcePack.getPackImage();
            } catch (IOException fallback) {
                return null;
            }
        }
    }
}

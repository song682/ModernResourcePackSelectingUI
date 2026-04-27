package decok.dfcdvadstf.modernresourcepack.mixins;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.List;

import decok.dfcdvadstf.modernresourcepack.resource.HighContrastResourcePack;
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
 * Injects the built-in High Contrast resource pack into the vanilla repository so it shows up
 * in the resource pack selection screen without the user having to drop any zip into resourcepacks/.
 */
@Mixin(ResourcePackRepository.class)
public class MixinResourcePackRepository {

    @Shadow
    private List repositoryEntriesAll;

    @Shadow
    public IMetadataSerializer rprMetadataSerializer;

    @Shadow
    public IResourcePack rprDefaultResourcePack;

    @Unique
    private static HighContrastResourcePack modernresourcepack$highContrastPack;

    @Unique
    private static ResourcePackRepository.Entry modernresourcepack$createEntry(ResourcePackRepository self, File file)
        throws Exception {
        // Constructor shape differs between dev/prod bytecode (synthetic bridge may be absent/present),
        // so we reflectively pick whatever ctor accepts (repo, file, ...) and fill the rest with defaults.
        Constructor<?>[] constructors = ResourcePackRepository.Entry.class.getDeclaredConstructors();
        for (Constructor<?> ctor : constructors) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length < 2) continue;
            if (!ResourcePackRepository.class.isAssignableFrom(params[0])) continue;
            if (!File.class.isAssignableFrom(params[1])) continue;

            Object[] args = new Object[params.length];
            args[0] = self;
            args[1] = file;
            for (int i = 2; i < params.length; i++) {
                args[i] = modernresourcepack$defaultArg(params[i]);
            }
            ctor.setAccessible(true);
            return (ResourcePackRepository.Entry) ctor.newInstance(args);
        }
        throw new NoSuchMethodException(
            "No compatible ResourcePackRepository.Entry constructor found in "
                + ResourcePackRepository.Entry.class.getName());
    }

    @Unique
    private static Object modernresourcepack$defaultArg(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "updateRepositoryEntriesAll", at = @At("RETURN"))
    private void modernresourcepack$injectHighContrast(CallbackInfo ci) {
        // Don't duplicate if already present (updateRepositoryEntriesAll can be called multiple times)
        for (Object obj : this.repositoryEntriesAll) {
            ResourcePackRepository.Entry entry = (ResourcePackRepository.Entry) obj;
            IResourcePack pack = entry.getResourcePack();
            if (pack != null && HighContrastResourcePack.PACK_NAME.equals(pack.getPackName())) {
                return;
            }
        }

        try {
            if (modernresourcepack$highContrastPack == null) {
                modernresourcepack$highContrastPack = new HighContrastResourcePack();
            }

            ResourcePackRepository self = (ResourcePackRepository) (Object) this;
            ResourcePackRepository.Entry entry = modernresourcepack$createEntry(
                self,
                new File(HighContrastResourcePack.INTERNAL_ID));

            // Populate Entry fields directly — no real file on disk, so updateResourcePack() can't run.
            ResourcePackEntryAccessor accessor = (ResourcePackEntryAccessor) entry;
            accessor.setReResourcePack(modernresourcepack$highContrastPack);

            PackMetadataSection metadata = (PackMetadataSection) modernresourcepack$highContrastPack
                .getPackMetadata(this.rprMetadataSerializer, "pack");
            accessor.setRePackMetadataSection(metadata);

            BufferedImage icon;
            try {
                icon = modernresourcepack$highContrastPack.getPackImage();
            } catch (Exception e) {
                icon = this.rprDefaultResourcePack.getPackImage();
            }
            accessor.setTexturePackIcon(icon);

            this.repositoryEntriesAll.add(entry);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

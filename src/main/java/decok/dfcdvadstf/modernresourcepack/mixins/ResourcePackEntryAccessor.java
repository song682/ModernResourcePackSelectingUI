package decok.dfcdvadstf.modernresourcepack.mixins;

import java.awt.image.BufferedImage;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.data.PackMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResourcePackRepository.Entry.class)
public interface ResourcePackEntryAccessor {

    @Accessor("rePackMetadataSection")
    PackMetadataSection getRePackMetadataSection();

    @Accessor("rePackMetadataSection")
    void setRePackMetadataSection(PackMetadataSection section);

    @Accessor("reResourcePack")
    void setReResourcePack(IResourcePack pack);

    @Accessor("texturePackIcon")
    void setTexturePackIcon(BufferedImage icon);
}

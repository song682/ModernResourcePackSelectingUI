package decok.dfcdvadstf.modernresourcepack.mixins;

import java.util.List;

import decok.dfcdvadstf.modernresourcepack.api.WorldResourcePack;
import decok.dfcdvadstf.modernresourcepack.api.WorldResourcePackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Splices the world-scoped resource pack onto the end of the resource pack list
 * that Minecraft is about to hand to the reload pipeline.
 *
 * Last element wins — that's how vanilla layering works, later packs override earlier
 * ones. So appending makes the world pack the top-most pack for the duration of the
 * session, above anything the user selected in the UI.
 *
 * We target the first {@code reloadResources} invocation only. The catch-block call
 * is the "everything failed, strip down to defaults" recovery path — stuffing our
 * pack in there would just reproduce the same crash on loop.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Redirect(method = "refreshResources",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/resources/IReloadableResourceManager;reloadResources(Ljava/util/List;)V",
                     ordinal = 0))
    private void modernrpui$appendWorldPack(IReloadableResourceManager manager, List packs) {
        if (WorldResourcePackManager.isActive()) {
            WorldResourcePack pack = WorldResourcePackManager.getActivePack();
            if (pack != null && !packs.contains(pack)) {
                packs.add(pack);
            }
        }
        manager.reloadResources(packs);
    }
}

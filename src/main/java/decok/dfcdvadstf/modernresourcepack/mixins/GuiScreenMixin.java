package decok.dfcdvadstf.modernresourcepack.mixins;

import decok.dfcdvadstf.modernresourcepack.utils.handlers.ResourcePackDropHandler;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public class GuiScreenMixin {
    @Inject(method = "onGuiClosed", at = @At("RETURN"))
    private void onGuiClosed(CallbackInfo ci) {
        if ((Object) this instanceof GuiScreenResourcePacks) {
            ResourcePackDropHandler.unregister();
        }
    }
}

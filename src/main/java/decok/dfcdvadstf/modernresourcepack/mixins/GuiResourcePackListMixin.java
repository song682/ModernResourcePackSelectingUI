package decok.dfcdvadstf.modernresourcepack.mixins;

import net.minecraft.client.gui.GuiResourcePackList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GuiResourcePackList.class)
public abstract class GuiResourcePackListMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiListExtended;<init>(Lnet/minecraft/client/Minecraft;IIIII)V"), index = 3)
    private static int modifyTop(int original) {
        return original == 32 ? 45 : original;
    }
}
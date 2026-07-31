package decok.dfcdvadstf.modernresourcepack.mixins;

import decok.dfcdvadstf.modernresourcepack.utils.handlers.ResourcePackDropHandler;
import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Linux-only drag-drop pump.
 *
 * Pumps native XDnD events out of LWJGL's X queue immediately before
 * {@code Display.update()} drains and discards them. {@code func_147120_f()} is
 * the MC 1.7.10 method whose first statement is {@code Display.update()}, so a
 * HEAD inject here is the last safe moment to grab the events ourselves.
 *
 * This mixin is gated to Linux by {@code PackUIMixinPlugin#shouldApplyMixin}:
 * on Windows the OS delivers drops through the subclassed WndProc and on macOS
 * through the Cocoa view, so this per-frame inject is pointless there.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftDragDropMixin {

    @Inject(method = "func_147120_f", at = @At("HEAD"))
    private void modernrpui$pumpDragDrop(CallbackInfo ci) {
        ResourcePackDropHandler.pumpNativeEvents();
    }
}

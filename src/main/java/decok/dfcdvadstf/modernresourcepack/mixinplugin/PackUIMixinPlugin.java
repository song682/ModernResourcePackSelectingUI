package decok.dfcdvadstf.modernresourcepack.mixinplugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin for the main (early) mixin set.
 *
 * Its only job right now is to gate the Linux-only native drag-drop pump so it
 * is applied exclusively on Linux/X11. On Windows the OS delivers drops through
 * the subclassed WndProc and on macOS through the Cocoa view, so injecting the
 * per-frame XDnD pump into {@code func_147120_f} there is dead weight.
 */
public class PackUIMixinPlugin implements IMixinConfigPlugin {

    /** Fully-qualified name of the mixin we want to restrict to Linux. */
    private static final String DRAGDROP_MIXIN =
            "decok.dfcdvadstf.modernresourcepack.mixins.MinecraftDragDropMixin";

    /**
     * Resolve the OS from the {@code os.name} system property rather than
     * {@link net.minecraft.util.Util}: this plugin runs during mixin bootstrap,
     * long before it is safe to load Minecraft classes through the transforming
     * class loader. {@code Util.getOSType()} internally reads the same property.
     */
    private static final boolean IS_LINUX =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");

    @Override
    public void onLoad(String s) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (DRAGDROP_MIXIN.equals(mixinClassName)) {
            return IS_LINUX;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> set, Set<String> set1) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }
}

package decok.dfcdvadstf.modernresourcepack.mixins;

import decok.dfcdvadstf.modernresourcepack.IncompatiblePackHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.data.PackMetadataSection;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResourcePackListEntry.class)
public abstract class ResourcePackListEntryMixin {

    @Shadow protected Minecraft field_148317_a;
    @Shadow protected GuiScreenResourcePacks field_148315_b;

    @Unique
    private static final ResourceLocation INCOMPATIBLE_ICON =
            new ResourceLocation("modernresourceselectui", "textures/gui/incompatible.png");

    @Unique
    private boolean modernresourcepack$hovered;

    @Unique
    private static boolean modernresourcepack$isIncompatible(ResourcePackListEntryFound entry) {
        ResourcePackRepository.Entry repoEntry = entry.func_148318_i();
        PackMetadataSection meta = ((ResourcePackEntryAccessor) repoEntry).getRePackMetadataSection();
        // null = no pack.mcmeta (older pack), pack_format != 1 = incompatible
        return meta == null || meta.getPackFormat() != 1;
    }

    @Unique
    private static boolean modernresourcepack$isNewer(ResourcePackListEntryFound entry) {
        ResourcePackRepository.Entry repoEntry = entry.func_148318_i();
        PackMetadataSection meta = ((ResourcePackEntryAccessor) repoEntry).getRePackMetadataSection();
        return meta != null && meta.getPackFormat() > 1;
    }

    // HEAD: red padding + capture hover state
    @Inject(method = "drawEntry", at = @At("HEAD"))
    private void onDrawEntryHead(int index, int x, int y, int listWidth, int slotHeight,
                                 Tessellator tess, int mouseX, int mouseY, boolean isHovered,
                                 CallbackInfo ci) {
        this.modernresourcepack$hovered = isHovered;

        if (!((Object) this instanceof ResourcePackListEntryFound)) return;
        if (!modernresourcepack$isIncompatible((ResourcePackListEntryFound) (Object) this)) return;

        // Red semi-transparent background — always visible
        Gui.drawRect(x, y, x + listWidth, y + slotHeight, 0x44FF0000);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // Replace resource_packs.png with incompatible.png for incompatible packs in the available list
    // This completely replaces the add button texture instead of drawing over it
    @Redirect(method = "drawEntry", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V"))
    private void redirectBindTexture(TextureManager manager, ResourceLocation original) {
        if ((Object) this instanceof ResourcePackListEntryFound
                && modernresourcepack$isIncompatible((ResourcePackListEntryFound) (Object) this)
                && this.func_148309_e()) {
            manager.bindTexture(INCOMPATIBLE_ICON);
        } else {
            manager.bindTexture(original);
        }
    }

    // Replace pack name with "Incompatible!" on hover
    @Redirect(method = "drawEntry", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/ResourcePackListEntry;func_148312_b()Ljava/lang/String;"))
    private String replaceNameWhenHovered(ResourcePackListEntry self) {
        if (this.modernresourcepack$hovered
                && (Object) this instanceof ResourcePackListEntryFound
                && modernresourcepack$isIncompatible((ResourcePackListEntryFound) (Object) this)) {
            return EnumChatFormatting.RED.toString() + EnumChatFormatting.BOLD
                    + I18n.format("gui.resourcepacks.incompatible");
        }
        return this.func_148312_b();
    }

    // Intercept click-to-add for incompatible packs — show GuiYesNo confirmation
    @Inject(method = "mousePressed", at = @At("HEAD"), cancellable = true)
    private void interceptIncompatibleAdd(int slotIndex, int x, int y, int mouseEvent,
                                          int relX, int relY,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ResourcePackListEntryFound)) return;
        ResourcePackListEntryFound self = (ResourcePackListEntryFound) (Object) this;
        if (!modernresourcepack$isIncompatible(self)) return;

        // Only intercept "add to selected" action (icon area click + pack is in available list)
        if (this.func_148310_d() && relX <= 32 && this.func_148309_e()) {
            IncompatiblePackHelper.setPendingEntry((ResourcePackListEntry) (Object) this);
            String messageKey = modernresourcepack$isNewer(self)
                    ? "gui.resourcepacks.incompatible.confirm.message.new"
                    : "gui.resourcepacks.incompatible.confirm.message.old";
            GuiYesNo confirm = new GuiYesNo(
                    (GuiYesNoCallback) this.field_148315_b,
                    I18n.format("gui.resourcepacks.incompatible.confirm.title"),
                    I18n.format(messageKey),
                    I18n.format("gui.yes"),
                    I18n.format("gui.no"),
                    1
            );
            this.field_148317_a.displayGuiScreen(confirm);
            cir.setReturnValue(true);
        }
    }

    @Shadow protected abstract String func_148312_b();
    @Shadow protected abstract boolean func_148310_d();
    @Shadow protected abstract boolean func_148309_e();
}

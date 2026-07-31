package decok.dfcdvadstf.modernresourcepack.mixins;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import decok.dfcdvadstf.modernresourcepack.utils.SupportedFormatRegistry;
import net.minecraft.client.resources.data.PackMetadataSection;
import net.minecraft.client.resources.data.PackMetadataSectionSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

/**
 * Parses the optional {@code supported_format} field from a pack.mcmeta's
 * {@code pack} section.
 *
 * Format: {@code "supported_format": [min, max]} — both must be plain ints and
 * must satisfy {@code 0 < min <= pack_format <= max < Integer.MAX_VALUE}.
 * Any violation throws {@link JsonParseException}, which vanilla's
 * deserialization pipeline bubbles up — the pack ends up with a null metadata
 * section and is refused.
 */
@Mixin(PackMetadataSectionSerializer.class)
public class PackMetadataSectionSerializerMixin {

    @Inject(method = "deserialize("
            + "Lcom/google/gson/JsonElement;"
            + "Ljava/lang/reflect/Type;"
            + "Lcom/google/gson/JsonDeserializationContext;"
            + ")Lnet/minecraft/client/resources/data/PackMetadataSection;",
            at = @At("RETURN"))
    private void modernresourcepack$parseSupportedFormat(JsonElement element,
                                                         Type type,
                                                         JsonDeserializationContext ctx,
                                                         CallbackInfoReturnable<PackMetadataSection> cir) {
        if (element == null || !element.isJsonObject()) return;
        JsonObject obj = element.getAsJsonObject();
        if (!obj.has("supported_format")) return;

        JsonElement sfEl = obj.get("supported_format");
        if (sfEl == null || !sfEl.isJsonArray()) {
            throw new JsonParseException(
                    "\"supported_format\" must be an array of two integers [min, max]");
        }
        JsonArray arr = sfEl.getAsJsonArray();
        if (arr.size() != 2) {
            throw new JsonParseException(
                    "\"supported_format\" must contain exactly 2 elements [min, max], got " + arr.size());
        }

        int min = modernresourcepack$readInt(arr.get(0), "supported_format[0] (min)");
        int max = modernresourcepack$readInt(arr.get(1), "supported_format[1] (max)");

        PackMetadataSection section = cir.getReturnValue();
        if (section == null) {
            // Shouldn't happen on a successful deserialize, but guard anyway
            throw new JsonParseException("Cannot attach supported_format to a null pack section");
        }
        int packFormat = section.getPackFormat();

        // 0 < min <= pack_format <= max < Integer.MAX_VALUE
        if (min <= 0) {
            throw new JsonParseException(
                    "\"supported_format\" min must be > 0, got " + min);
        }
        if (max >= Integer.MAX_VALUE) {
            throw new JsonParseException(
                    "\"supported_format\" max must be < Integer.MAX_VALUE, got " + max);
        }
        if (min > max) {
            throw new JsonParseException(
                    "\"supported_format\" min (" + min + ") must be <= max (" + max + ")");
        }
        if (packFormat < min || packFormat > max) {
            throw new JsonParseException(
                    "pack_format (" + packFormat + ") must be within supported_format ["
                            + min + ", " + max + "]");
        }

        SupportedFormatRegistry.register(section, min, max);
    }

    private static int modernresourcepack$readInt(JsonElement el, String fieldLabel) {
        if (el == null || !el.isJsonPrimitive()) {
            throw new JsonParseException(fieldLabel + " must be an int");
        }
        JsonPrimitive prim = el.getAsJsonPrimitive();
        if (!prim.isNumber()) {
            throw new JsonParseException(fieldLabel + " must be an int, got " + prim);
        }
        // Reject floats/doubles — user explicitly required int typing
        double d;
        try {
            d = prim.getAsDouble();
        } catch (NumberFormatException nfe) {
            throw new JsonParseException(fieldLabel + " is not a valid number", nfe);
        }
        if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.floor(d)) {
            throw new JsonParseException(fieldLabel + " must be an integer, got " + prim);
        }
        if (d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
            throw new JsonParseException(fieldLabel + " is out of int range: " + prim);
        }
        return (int) d;
    }
}

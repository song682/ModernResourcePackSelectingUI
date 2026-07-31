package decok.dfcdvadstf.modernresourcepack.resource;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import com.google.common.base.Charsets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Virtual resource pack backed entirely by files inside the mod jar at /high_contrast/.
 * Nothing touches disk — users can enable/disable it from the vanilla UI, but they can't
 * grab a folder and redistribute it.
 *
 * Implementation note: this class is just thin glue over {@link Class#getResourceAsStream},
 * which is the straightforward way to wrap jar-embedded assets into Mojang's
 * {@link IResourcePack}. Pack metadata is parsed from the real pack.mcmeta shipped in the jar,
 * so description/pack_format stay in sync with the file without any hardcoded strings.
 */
public class HighContrastPack implements IResourcePack {

    public static final String DISPLAY_NAME = "High Contrast";
    public static final String VIRTUAL_ID = "high_contrast";

    private static final String JAR_ROOT = "/" + VIRTUAL_ID + "/";
    private static final Set<String> SUPPORTED_DOMAINS = Collections
        .unmodifiableSet(new HashSet<String>(Arrays.asList("minecraft", "createworldui")));

    @Override
    public String getPackName() {
        return DISPLAY_NAME;
    }

    @Override
    public Set<String> getResourceDomains() {
        return SUPPORTED_DOMAINS;
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        return openOrThrow(resolveAssetPath(location));
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        return classpathHas(resolveAssetPath(location));
    }

    @Override
    public IMetadataSection getPackMetadata(IMetadataSerializer serializer, String sectionName) throws IOException {
        InputStream in = openOrThrow("pack.mcmeta");
        try {
            Reader reader = new InputStreamReader(in, Charsets.UTF_8);
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            return serializer.parseMetadataSection(sectionName, root);
        } finally {
            closeQuiet(in);
        }
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        InputStream in = openOrThrow("pack.png");
        try {
            return ImageIO.read(in);
        } finally {
            closeQuiet(in);
        }
    }

    // ---- helpers ----

    private static String resolveAssetPath(ResourceLocation location) {
        return "assets/" + location.getResourceDomain() + '/' + location.getResourcePath();
    }

    private InputStream openOrThrow(String relativeInsidePack) throws IOException {
        InputStream in = getClass().getResourceAsStream(JAR_ROOT + relativeInsidePack);
        if (in == null) {
            throw new FileNotFoundException(JAR_ROOT + relativeInsidePack);
        }
        return in;
    }

    private boolean classpathHas(String relativeInsidePack) {
        return getClass().getResource(JAR_ROOT + relativeInsidePack) != null;
    }

    private static void closeQuiet(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (IOException ignored) {}
    }
}

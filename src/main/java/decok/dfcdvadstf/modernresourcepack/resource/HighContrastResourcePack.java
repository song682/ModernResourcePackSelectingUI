package decok.dfcdvadstf.modernresourcepack.resource;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;

import javax.imageio.ImageIO;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Built-in virtual resource pack providing high contrast UI textures.
 * Lives inside the mod jar under /high_contrast/, so users don't have to install anything separately.
 */
public class HighContrastResourcePack implements IResourcePack {

    public static final String PACK_NAME = "High Contrast";
    public static final String INTERNAL_ID = "high_contrast";

    private static final String PREFIX = "/high_contrast/";
    private static final String PACK_META_JSON = "{"
        + "\"pack\":{"
        + "\"description\":\"Enhances the UI contrast of Minecraft\","
        + "\"pack_format\":1"
        + "}"
        + "}";

    private static final Set<String> DOMAINS = Sets.newHashSet("minecraft", "createworldui");

    private String locationToName(ResourceLocation loc) {
        return String.format("assets/%s/%s", loc.getResourceDomain(), loc.getResourcePath());
    }

    @Override
    public InputStream getInputStream(ResourceLocation loc) throws IOException {
        String path = PREFIX + locationToName(loc);
        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
            throw new IOException("Resource not found: " + loc);
        }
        return is;
    }

    @Override
    public boolean resourceExists(ResourceLocation loc) {
        String path = PREFIX + locationToName(loc);
        return getClass().getResource(path) != null;
    }

    @Override
    public Set<String> getResourceDomains() {
        return DOMAINS;
    }

    @Override
    public IMetadataSection getPackMetadata(IMetadataSerializer serializer, String section) throws IOException {
        // Try the real pack.mcmeta first — keeps description in sync with the file
        InputStream metaStream = getClass().getResourceAsStream(PREFIX + "pack.mcmeta");
        if (metaStream == null) {
            metaStream = new ByteArrayInputStream(PACK_META_JSON.getBytes(Charsets.UTF_8));
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(metaStream, Charsets.UTF_8));
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            return serializer.parseMetadataSection(section, json);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        InputStream is = getClass().getResourceAsStream(PREFIX + "pack.png");
        if (is == null) {
            throw new IOException("No pack image found for " + PACK_NAME);
        }
        return ImageIO.read(is);
    }

    @Override
    public String getPackName() {
        return PACK_NAME;
    }
}

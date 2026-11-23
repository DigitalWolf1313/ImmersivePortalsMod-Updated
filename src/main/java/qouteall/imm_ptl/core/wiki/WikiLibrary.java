package qouteall.imm_ptl.core.wiki;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the wiki index, the pages and the image sizes from the mod resources.
 * An instance caches everything it reads, so a screen should create a new one when it opens.
 */
@Environment(EnvType.CLIENT)
public final class WikiLibrary {
    private static final String NAMESPACE = "immersive_portals";
    
    public record PageInfo(String slug, String title, String file, boolean outdated) {}
    
    public record Category(String name, List<PageInfo> pages) {}
    
    private final List<Category> categories = new ArrayList<>();
    private final Map<String, PageInfo> pageInfos = new LinkedHashMap<>();
    private final Map<String, WikiDocument> documents = new HashMap<>();
    private final Map<String, int[]> imageSizes = new HashMap<>();
    private String defaultLanguage = "en_us";
    
    public WikiLibrary() {
        loadIndex();
    }
    
    private void loadIndex() {
        String text = readText("wiki/_index.json");
        if (text == null) {
            Helper.err("Wiki index is missing");
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (root.has("default_lang")) {
                defaultLanguage = root.get("default_lang").getAsString();
            }
            
            JsonObject pages = root.getAsJsonObject("pages");
            for (Map.Entry<String, JsonElement> entry : pages.entrySet()) {
                JsonObject page = entry.getValue().getAsJsonObject();
                String slug = entry.getKey();
                pageInfos.put(slug, new PageInfo(
                    slug,
                    page.get("title").getAsString(),
                    page.get("file").getAsString(),
                    page.has("outdated") && page.get("outdated").getAsBoolean()
                ));
            }
            
            for (JsonElement categoryElement : root.getAsJsonArray("categories")) {
                JsonObject category = categoryElement.getAsJsonObject();
                List<PageInfo> infos = new ArrayList<>();
                JsonArray slugs = category.getAsJsonArray("pages");
                for (JsonElement slug : slugs) {
                    PageInfo info = pageInfos.get(slug.getAsString());
                    if (info != null) {
                        infos.add(info);
                    }
                }
                categories.add(new Category(category.get("name").getAsString(), infos));
            }
            Helper.log("Wiki index loaded with " + pageInfos.size() + " pages");
        }
        catch (RuntimeException e) {
            Helper.err("Cannot parse the wiki index " + e);
        }
    }
    
    public List<Category> getCategories() {
        return categories;
    }
    
    public List<String> getSlugs() {
        return List.copyOf(pageInfos.keySet());
    }
    
    @Nullable
    public PageInfo getPageInfo(String slug) {
        return pageInfos.get(slug);
    }
    
    public String getFirstSlug() {
        if (pageInfos.containsKey("index")) {
            return "index";
        }
        return pageInfos.isEmpty() ? "" : pageInfos.keySet().iterator().next();
    }
    
    /**
     * Returns the page in the selected language, or in the default language when it is missing.
     */
    @Nullable
    public WikiDocument getDocument(String slug) {
        if (documents.containsKey(slug)) {
            return documents.get(slug);
        }
        WikiDocument document = null;
        PageInfo info = pageInfos.get(slug);
        if (info != null) {
            String selected = Minecraft.getInstance().getLanguageManager().getSelected();
            List<String> languages = new ArrayList<>();
            languages.add(selected);
            if (!languages.contains(defaultLanguage)) {
                languages.add(defaultLanguage);
            }
            for (String language : languages) {
                String text = readText("wiki/" + language + "/" + info.file());
                if (text != null) {
                    document = WikiParser.parse(text);
                    break;
                }
            }
            if (document == null) {
                Helper.err("Cannot load the wiki page " + slug);
            }
        }
        documents.put(slug, document);
        return document;
    }
    
    /**
     * Returns the pages whose title matches first and then the pages whose text matches.
     */
    public List<PageInfo> search(String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        List<PageInfo> titleHits = new ArrayList<>();
        List<PageInfo> bodyHits = new ArrayList<>();
        for (PageInfo info : pageInfos.values()) {
            if (info.title().toLowerCase(Locale.ROOT).contains(q)) {
                titleHits.add(info);
                continue;
            }
            WikiDocument document = getDocument(info.slug());
            if (document != null && document.searchText().contains(q)) {
                bodyHits.add(info);
            }
        }
        titleHits.addAll(bodyHits);
        return titleHits;
    }
    
    public static ResourceLocation imageLocation(String path) {
        String p = path;
        while (p.startsWith("./") || p.startsWith("/")) {
            p = p.substring(p.startsWith("./") ? 2 : 1);
        }
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "wiki/" + p);
    }
    
    /**
     * Returns the pixel size of a PNG by reading only its header, or null when it cannot be read.
     */
    @Nullable
    public int[] getImageSize(String path) {
        if (imageSizes.containsKey(path)) {
            return imageSizes.get(path);
        }
        int[] size = null;
        if (!path.startsWith("http")) {
            boolean inJar = false;
            try {
                Optional<Resource> resource =
                    Minecraft.getInstance().getResourceManager().getResource(imageLocation(path));
                if (resource.isPresent()) {
                    inJar = true;
                    try (InputStream in = resource.get().open()) {
                        byte[] h = in.readNBytes(24);
                        if (h.length == 24 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
                            size = new int[]{readInt(h, 16), readInt(h, 20)};
                        }
                    }
                }
            }
            catch (IOException | RuntimeException e) {
                Helper.err("Cannot read the wiki image " + path + " " + e);
            }
            
            if (!inJar) {
                size = WikiRemoteImages.getSize(path);
                
                // A pending download is not cached, so the next layout asks again.
                if (size == null && WikiRemoteImages.isPending(path)) {
                    return null;
                }
            }
            if (size == null) {
                Helper.err("Wiki image unavailable: " + path);
            }
        }
        imageSizes.put(path, size);
        return size;
    }
    
    private static int readInt(byte[] b, int offset) {
        return ((b[offset] & 255) << 24) | ((b[offset + 1] & 255) << 16)
            | ((b[offset + 2] & 255) << 8) | (b[offset + 3] & 255);
    }
    
    @Nullable
    private static String readText(String path) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            Helper.err("Cannot read " + location + " " + e);
            return null;
        }
    }
}

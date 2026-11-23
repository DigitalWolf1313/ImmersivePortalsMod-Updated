package qouteall.imm_ptl.core.wiki;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Streams wiki images that are not in the mod jar from the image repository.
 * Each downloaded file is cached on disk and reused in later sessions.
 */
@Environment(EnvType.CLIENT)
public final class WikiRemoteImages {
    /**
     * The image repository URL with a trailing slash.
     * The image path from the wiki page is appended to it.
     */
    public static final String BASE_URL = "https://raw.githubusercontent.com/DigitalWolf1313/ImmersivePortalsMod-WikiImages/main/";
    
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    
    private enum State {LOADING, READY, FAILED}
    
    private static final class Entry {
        final String id;
        volatile State state = State.LOADING;
        volatile int width;
        volatile int height;
        volatile Path file;
        
        // Only accessed on the render thread.
        @Nullable
        ResourceLocation texture;
        
        Entry(String id) {
            this.id = id;
        }
    }
    
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final AtomicInteger VERSION = new AtomicInteger();
    
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "IP Wiki Image Download");
        thread.setDaemon(true);
        return thread;
    });
    
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    
    private WikiRemoteImages() {
    }
    
    /**
     * Returns the pixel size when the image has been downloaded.
     * Returns null while it is loading or when it failed. The first call starts the download.
     */
    @Nullable
    public static int[] getSize(String path) {
        Entry entry = ENTRIES.computeIfAbsent(path, WikiRemoteImages::start);
        return entry.state == State.READY ? new int[]{entry.width, entry.height} : null;
    }
    
    public static boolean isPending(String path) {
        Entry entry = ENTRIES.get(path);
        return entry != null && entry.state == State.LOADING;
    }
    
    public static boolean isRemote(String path) {
        return ENTRIES.containsKey(path);
    }
    
    /**
     * Returns a number that changes whenever a download finishes or fails.
     */
    public static int version() {
        return VERSION.get();
    }
    
    /**
     * Forgets the failed downloads so the next layout tries them again.
     */
    public static void retryFailed() {
        ENTRIES.values().removeIf(entry -> entry.state == State.FAILED);
    }
    
    /**
     * Returns the texture of a downloaded image and registers it on first use.
     * Must be called on the render thread.
     */
    @Nullable
    public static ResourceLocation ensureTexture(String path) {
        Entry entry = ENTRIES.get(path);
        if (entry == null || entry.state != State.READY) {
            return null;
        }
        if (entry.texture == null) {
            try (InputStream in = Files.newInputStream(entry.file)) {
                NativeImage image = NativeImage.read(in);
                DynamicTexture texture = new DynamicTexture(image);
                ResourceLocation location =
                    ResourceLocation.fromNamespaceAndPath("immersive_portals", "wiki_remote/" + entry.id);
                Minecraft.getInstance().getTextureManager().register(location, texture);
                texture.setFilter(true, false);
                entry.texture = location;
            }
            catch (IOException | RuntimeException e) {
                Helper.err("Cannot read the cached wiki image " + path + " " + e);
                entry.state = State.FAILED;
                VERSION.incrementAndGet();
                return null;
            }
        }
        return entry.texture;
    }
    
    public static void releaseTextures() {
        for (Entry entry : ENTRIES.values()) {
            if (entry.texture != null) {
                Minecraft.getInstance().getTextureManager().release(entry.texture);
                entry.texture = null;
            }
        }
    }
    
    private static Entry start(String path) {
        String url = urlOf(path);
        Entry entry = new Entry(UUID.nameUUIDFromBytes(String.valueOf(url).getBytes(StandardCharsets.UTF_8)).toString());
        if (url == null) {
            entry.state = State.FAILED;
            return entry;
        }
        EXECUTOR.execute(() -> load(url, path, entry));
        return entry;
    }
    
    @Nullable
    private static String urlOf(String path) {
        String p = path;
        while (p.startsWith("./") || p.startsWith("/")) {
            p = p.substring(p.startsWith("./") ? 2 : 1);
        }
        if (p.isEmpty() || p.contains("..") || p.contains(":") || p.contains("\\")) {
            return null;
        }
        return BASE_URL + p.replace(" ", "%20");
    }
    
    private static void load(String url, String path, Entry entry) {
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("immersive_portals_wiki_cache").resolve(entry.id + ".png");
            if (!Files.exists(file)) {
                download(url, file);
            }
            int[] size = readPngSize(file);
            if (size == null) {
                Files.deleteIfExists(file);
                throw new IOException("It is not a PNG file");
            }
            entry.file = file;
            entry.width = size[0];
            entry.height = size[1];
            entry.state = State.READY;
        }
        catch (Exception e) {
            Helper.err("Cannot load the wiki image " + path + " from " + url + " " + e);
            entry.state = State.FAILED;
        }
        VERSION.incrementAndGet();
    }
    
    private static void download(String url, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            byte[] data = in.readNBytes((int) MAX_BYTES + 1);
            if (data.length > MAX_BYTES) {
                throw new IOException("The image is too large");
            }
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".part");
            Files.write(temp, data);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    @Nullable
    private static int[] readPngSize(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] h = in.readNBytes(24);
            if (h.length == 24 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
                return new int[]{readInt(h, 16), readInt(h, 20)};
            }
            return null;
        }
    }
    
    private static int readInt(byte[] b, int offset) {
        return ((b[offset] & 255) << 24) | ((b[offset + 1] & 255) << 16)
            | ((b[offset + 2] & 255) << 8) | (b[offset + 3] & 255);
    }
}

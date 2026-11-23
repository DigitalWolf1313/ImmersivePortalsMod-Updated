package qouteall.imm_ptl.core.wiki;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import qouteall.q_misc_util.Helper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The in-game wiki browser. It has a page list with search on the left and the scrolling page on the right.
 */
@Environment(EnvType.CLIENT)
public class WikiScreen extends Screen {
    private static final int HEADER_HEIGHT = 30;
    private static final int ROW_HEIGHT = 12;
    private static final int SCROLLBAR_WIDTH = 6;
    
    private static final class HistoryEntry {
        final String slug;
        double scroll = 0;
        Set<Integer> expanded = new HashSet<>();
        
        HistoryEntry(String slug) {
            this.slug = slug;
        }
        
        void save(double scroll, Set<Integer> expanded) {
            this.scroll = scroll;
            this.expanded = new HashSet<>(expanded);
        }
    }
    
    /**
     * A sidebar row. A null page means a category header.
     */
    private record SideRow(@Nullable WikiLibrary.PageInfo page, String label) {}
    
    @Nullable
    private final Screen parent;
    @Nullable
    private final String initialSlug;
    @Nullable
    private final String initialAnchor;
    
    private final WikiLibrary library = new WikiLibrary();
    private final List<HistoryEntry> history = new ArrayList<>();
    private final Set<Integer> expanded = new HashSet<>();
    private final Set<ResourceLocation> configuredTextures = new HashSet<>();
    private final List<SideRow> sideRows = new ArrayList<>();
    
    private int historyIndex = -1;
    private String currentSlug = "";
    @Nullable
    private WikiDocument document;
    @Nullable
    private WikiLayout.Result layout;
    private double scroll = 0;
    private double sideScroll = 0;
    private String searchText = "";
    @Nullable
    private String zoomedImage;
    private boolean draggingScrollbar = false;
    private double dragOffset = 0;
    private int seenImageVersion;
    
    private int sideWidth;
    private int contentX;
    private int contentWidth;
    private int contentTop;
    private int contentBottom;
    
    private EditBox searchBox;
    private Button backButton;
    private Button forwardButton;
    
    public WikiScreen(@Nullable Screen parent, @Nullable String slug, @Nullable String anchor) {
        super(Component.translatable("imm_ptl.wiki.title"));
        this.parent = parent;
        this.initialSlug = slug;
        this.initialAnchor = anchor;
        WikiRemoteImages.retryFailed();
    }
    
    @Override
    protected void init() {
        seenImageVersion = WikiRemoteImages.version();
        sideWidth = Mth.clamp(width / 4, 100, 170);
        contentX = sideWidth + 12;
        contentWidth = Math.max(60, width - contentX - 16);
        contentTop = HEADER_HEIGHT + 4;
        contentBottom = height - 6;
        
        backButton = addRenderableWidget(
            Button.builder(Component.literal("<"), b -> goBack()).bounds(6, 5, 20, 20).build()
        );
        forwardButton = addRenderableWidget(
            Button.builder(Component.literal(">"), b -> goForward()).bounds(28, 5, 20, 20).build()
        );
        addRenderableWidget(
            Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width - 66, 5, 60, 20).build()
        );
        
        searchBox = new EditBox(font, 6, HEADER_HEIGHT + 4, sideWidth - 12, 16, Component.translatable("imm_ptl.wiki.search"));
        searchBox.setHint(Component.translatable("imm_ptl.wiki.search").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setValue(searchText);
        searchBox.setResponder(text -> {
            searchText = text;
            sideScroll = 0;
            rebuildSidebar();
        });
        addRenderableWidget(searchBox);
        
        rebuildSidebar();
        
        if (history.isEmpty()) {
            String slug = initialSlug == null || initialSlug.isEmpty() ? library.getFirstSlug() : initialSlug;
            if (library.getPageInfo(slug) == null) {
                slug = library.getFirstSlug();
            }
            navigate(slug, initialAnchor, true);
        }
        else {
            relayout();
        }
    }
    
    private int sidebarTop() {
        return HEADER_HEIGHT + 24;
    }
    
    private void rebuildSidebar() {
        sideRows.clear();
        String query = searchText.strip();
        if (query.isEmpty()) {
            for (WikiLibrary.Category category : library.getCategories()) {
                sideRows.add(new SideRow(null, category.name()));
                for (WikiLibrary.PageInfo page : category.pages()) {
                    sideRows.add(new SideRow(page, page.title()));
                }
            }
        }
        else {
            for (WikiLibrary.PageInfo page : library.search(query)) {
                sideRows.add(new SideRow(page, page.title()));
            }
        }
    }
    
    private void navigate(String slug, @Nullable String anchor, boolean record) {
        WikiLibrary.PageInfo info = library.getPageInfo(slug);
        if (info == null) {
            Helper.err("Wiki page not found: " + slug);
            return;
        }
        
        boolean sameAsCurrent = anchor == null && historyIndex >= 0
            && history.get(historyIndex).slug.equals(slug);
        if (record && !sameAsCurrent) {
            if (historyIndex >= 0) {
                history.get(historyIndex).save(scroll, expanded);
            }
            while (history.size() > historyIndex + 1) {
                history.remove(history.size() - 1);
            }
            history.add(new HistoryEntry(slug));
            historyIndex = history.size() - 1;
        }
        
        boolean samePage = slug.equals(currentSlug) && document != null;
        if (!samePage) {
            currentSlug = slug;
            document = library.getDocument(slug);
            expanded.clear();
            scroll = 0;
        }
        
        if (anchor != null && document != null) {
            Integer container = document.anchorContainers().get(anchor);
            if (container != null && container >= 0) {
                expanded.add(container);
            }
        }
        relayout();
        
        if (anchor != null && layout != null) {
            Integer anchorY = layout.anchors().get(anchor);
            if (anchorY != null) {
                scroll = anchorY;
                clampScroll();
            }
            else {
                Helper.err("Wiki anchor not found: " + slug + "#" + anchor);
            }
        }
    }
    
    private void restore(HistoryEntry entry) {
        if (!entry.slug.equals(currentSlug) || document == null) {
            currentSlug = entry.slug;
            document = library.getDocument(entry.slug);
        }
        expanded.clear();
        expanded.addAll(entry.expanded);
        relayout();
        scroll = entry.scroll;
        clampScroll();
    }
    
    private void goBack() {
        if (historyIndex > 0) {
            history.get(historyIndex).save(scroll, expanded);
            historyIndex--;
            restore(history.get(historyIndex));
        }
    }
    
    private void goForward() {
        if (historyIndex < history.size() - 1) {
            history.get(historyIndex).save(scroll, expanded);
            historyIndex++;
            restore(history.get(historyIndex));
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        int version = WikiRemoteImages.version();
        if (version != seenImageVersion) {
            seenImageVersion = version;
            relayout();
        }
    }
    
    private void relayout() {
        if (document == null) {
            layout = null;
            return;
        }
        layout = new WikiLayout(font, library, expanded, minecraft.getWindow().getGuiScale())
            .layout(document, contentWidth);
        clampScroll();
    }
    
    private int viewHeight() {
        return contentBottom - contentTop;
    }
    
    private double maxScroll() {
        return layout == null ? 0 : Math.max(0, layout.height() - viewHeight());
    }
    
    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }
    
    private double maxSideScroll() {
        return Math.max(0, sideRows.size() * ROW_HEIGHT - (height - 4 - sidebarTop()));
    }
    
    private boolean inContent(double mx, double my) {
        return mx >= contentX && mx < contentX + contentWidth && my >= contentTop && my < contentBottom;
    }
    
    private int scrollbarX() {
        return contentX + contentWidth + 4;
    }
    
    private int thumbHeight() {
        if (layout == null || layout.height() <= 0) {
            return viewHeight();
        }
        return Math.max(20, (int) ((double) viewHeight() * viewHeight() / layout.height()));
    }
    
    private int thumbTop() {
        double max = maxScroll();
        if (max <= 0) {
            return contentTop;
        }
        return contentTop + (int) (scroll / max * (viewHeight() - thumbHeight()));
    }
    
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, HEADER_HEIGHT, sideWidth, height, 0x90000000);
        g.fill(sideWidth + 4, contentTop - 2, width - 4, contentBottom + 2, 0x90000000);
    }
    
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        backButton.active = historyIndex > 0;
        forwardButton.active = historyIndex < history.size() - 1;
        
        super.render(g, mouseX, mouseY, partialTick);
        
        WikiLibrary.PageInfo info = library.getPageInfo(currentSlug);
        String pageTitle = info == null ? "" : document == null ? info.title() : document.title(info.title());
        g.drawCenteredString(font, Component.literal(pageTitle), width / 2, 11, 0xFFFFFFFF);
        
        renderSidebar(g, mouseX, mouseY);
        renderContent(g, mouseX, mouseY);
        renderScrollbar(g);
        
        if (zoomedImage != null) {
            renderZoom(g);
        }
    }
    
    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int top = sidebarTop();
        int bottom = height - 4;
        g.enableScissor(0, top, sideWidth, bottom);
        
        int y = top - (int) sideScroll;
        if (sideRows.isEmpty()) {
            g.drawString(font, Component.translatable("imm_ptl.wiki.no_results"), 8, y + 2, WikiLayout.DIM_COLOR);
        }
        for (SideRow row : sideRows) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                WikiLibrary.PageInfo page = row.page();
                if (page == null) {
                    g.drawString(font, Component.literal(row.label()).withStyle(ChatFormatting.BOLD), 6, y + 2, 0xFFFFD27F);
                }
                else {
                    boolean hovered = mouseX >= 0 && mouseX < sideWidth
                        && mouseY >= Math.max(y, top) && mouseY < Math.min(y + ROW_HEIGHT, bottom);
                    if (page.slug().equals(currentSlug)) {
                        g.fill(2, y, sideWidth - 2, y + ROW_HEIGHT, 0x50FFFFFF);
                    }
                    else if (hovered) {
                        g.fill(2, y, sideWidth - 2, y + ROW_HEIGHT, 0x28FFFFFF);
                    }
                    int color = page.outdated() ? WikiLayout.DIM_COLOR : 0xFFFFFFFF;
                    g.drawString(font, font.plainSubstrByWidth(row.label(), sideWidth - 18), 12, y + 2, color);
                }
            }
            y += ROW_HEIGHT;
        }
        g.disableScissor();
    }
    
    private void renderContent(GuiGraphics g, int mouseX, int mouseY) {
        g.enableScissor(contentX, contentTop, contentX + contentWidth, contentBottom);
        if (layout == null) {
            g.drawString(font, Component.translatable("imm_ptl.wiki.missing_page"), contentX + 4, contentTop + 4, 0xFFFFAAAA);
        }
        else {
            int originY = contentTop - (int) scroll;
            for (WikiLayout.Cmd cmd : layout.cmds()) {
                int sy = originY + cmd.y();
                if (sy + cmd.height() < contentTop || sy > contentBottom) {
                    continue;
                }
                switch (cmd) {
                    case WikiLayout.Cmd.Text t -> drawText(g, t, contentX + t.x(), sy);
                    case WikiLayout.Cmd.Fill f ->
                        g.fill(contentX + f.x(), sy, contentX + f.x() + f.width(), sy + f.height(), f.color());
                    case WikiLayout.Cmd.Image im -> drawImage(g, im, contentX + im.x(), sy);
                    case WikiLayout.Cmd.Toggle ignored -> {
                    }
                }
            }
        }
        g.disableScissor();
        
        if (zoomedImage == null) {
            String link = linkAt(mouseX, mouseY);
            if (link != null) {
                g.renderTooltip(font, linkTooltip(link), mouseX, mouseY);
            }
        }
    }
    
    private void drawText(GuiGraphics g, WikiLayout.Cmd.Text t, int x, int y) {
        if (t.scale() == 1f) {
            g.drawString(font, t.text(), x, y, t.color());
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(t.scale(), t.scale(), 1f);
        g.drawString(font, t.text(), 0, 0, t.color());
        g.pose().popPose();
    }
    
    private void drawImage(GuiGraphics g, WikiLayout.Cmd.Image im, int x, int y) {
        ResourceLocation location = ensureTexture(im.path());
        if (location == null) {
            return;
        }
        g.blit(location, x, y, im.width(), im.height(), 0f, 0f, im.texWidth(), im.texHeight(), im.texWidth(), im.texHeight());
        g.renderOutline(x - 1, y - 1, im.width() + 2, im.height() + 2, 0x60FFFFFF);
    }
    
    /**
     * Loads the texture on first use and switches it to linear filtering so shrunk screenshots look smooth.
     */
    @Nullable
    private ResourceLocation ensureTexture(String path) {
        ResourceLocation remote = WikiRemoteImages.ensureTexture(path);
        if (remote != null) {
            return remote;
        }
        if (WikiRemoteImages.isRemote(path)) {
            return null;
        }
        ResourceLocation location = WikiLibrary.imageLocation(path);
        if (configuredTextures.add(location)) {
            minecraft.getTextureManager().getTexture(location).setFilter(true, false);
        }
        return location;
    }
    
    private void renderScrollbar(GuiGraphics g) {
        if (maxScroll() <= 0) {
            return;
        }
        int x = scrollbarX();
        g.fill(x, contentTop, x + SCROLLBAR_WIDTH, contentBottom, 0x55000000);
        int top = thumbTop();
        g.fill(x, top, x + SCROLLBAR_WIDTH, top + thumbHeight(), draggingScrollbar ? 0xFFCCCCCC : 0xFF888888);
    }
    
    private void renderZoom(GuiGraphics g) {
        int[] size = library.getImageSize(zoomedImage);
        if (size == null) {
            zoomedImage = null;
            return;
        }
        ResourceLocation location = ensureTexture(zoomedImage);
        if (location == null) {
            zoomedImage = null;
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, width, height, 0xE6000000);
        
        double fit = Math.min(3.0, Math.min((width - 24.0) / size[0], (height - 40.0) / size[1]));
        int w = Math.max(1, (int) (size[0] * fit));
        int h = Math.max(1, (int) (size[1] * fit));
        g.blit(location, (width - w) / 2, (height - h) / 2 - 6, w, h, 0f, 0f, size[0], size[1], size[0], size[1]);
        g.drawCenteredString(font, Component.translatable("imm_ptl.wiki.close_image"), width / 2, height - 16, 0xFFAAAAAA);
        g.pose().popPose();
    }
    
    @Nullable
    private String linkAt(double mx, double my) {
        if (layout == null || !inContent(mx, my)) {
            return null;
        }
        int originY = contentTop - (int) scroll;
        for (WikiLayout.Cmd cmd : layout.cmds()) {
            if (!(cmd instanceof WikiLayout.Cmd.Text t)) {
                continue;
            }
            double lx = mx - contentX - t.x();
            double ly = my - (originY + t.y());
            if (lx < 0 || ly < 0 || lx >= t.width() || ly >= t.height()) {
                continue;
            }
            Style style = font.getSplitter().componentStyleAtWidth(t.text(), (int) (lx / t.scale()));
            if (style != null && style.getClickEvent() != null
                && style.getClickEvent().getAction() == ClickEvent.Action.OPEN_URL
            ) {
                return style.getClickEvent().getValue();
            }
        }
        return null;
    }
    
    private Component linkTooltip(String link) {
        if (link.startsWith("wiki:") || link.startsWith("#")) {
            String[] target = parseInternalLink(link);
            WikiLibrary.PageInfo info = library.getPageInfo(target[0]);
            String label = info == null ? target[0] : info.title();
            return Component.literal(target[1] == null ? label : label + " > #" + target[1]);
        }
        return Component.literal(link);
    }
    
    /**
     * Returns the page slug and the anchor, where the anchor is null when the link has none.
     */
    private String[] parseInternalLink(String link) {
        String rest = link.startsWith("wiki:") ? link.substring(5) : link;
        int hash = rest.indexOf('#');
        String slug = hash >= 0 ? rest.substring(0, hash) : rest;
        String anchor = hash >= 0 ? rest.substring(hash + 1) : null;
        return new String[]{slug.isEmpty() ? currentSlug : slug, anchor};
    }
    
    private void openLink(String link) {
        if (link.startsWith("wiki:") || link.startsWith("#")) {
            String[] target = parseInternalLink(link);
            navigate(target[0], target[1], true);
        }
        else if (link.startsWith("http://") || link.startsWith("https://")) {
            minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
                if (confirmed) {
                    Util.getPlatform().openUri(link);
                }
                minecraft.setScreen(this);
            }, link, true));
        }
        else {
            Helper.err("Unsupported wiki link: " + link);
        }
    }
    
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (zoomedImage != null) {
            zoomedImage = null;
            return true;
        }
        if (button == 3) {
            goBack();
            return true;
        }
        if (button == 4) {
            goForward();
            return true;
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        
        if (mx >= 0 && mx < sideWidth && my >= sidebarTop() && my < height - 4) {
            setFocused(null);
            int index = (int) ((my - sidebarTop() + sideScroll) / ROW_HEIGHT);
            if (index >= 0 && index < sideRows.size()) {
                WikiLibrary.PageInfo page = sideRows.get(index).page();
                if (page != null) {
                    navigate(page.slug(), null, true);
                }
            }
            return true;
        }
        
        if (maxScroll() > 0 && mx >= scrollbarX() && mx < scrollbarX() + SCROLLBAR_WIDTH
            && my >= contentTop && my < contentBottom
        ) {
            int top = thumbTop();
            boolean onThumb = my >= top && my < top + thumbHeight();
            draggingScrollbar = true;
            dragOffset = onThumb ? my - top : thumbHeight() / 2.0;
            mouseDragged(mx, my, button, 0, 0);
            return true;
        }
        
        if (layout != null && inContent(mx, my)) {
            setFocused(null);
            int originY = contentTop - (int) scroll;
            for (WikiLayout.Cmd cmd : layout.cmds()) {
                double lx = mx - contentX - cmd.x();
                double ly = my - (originY + cmd.y());
                boolean inside = lx >= 0 && ly >= 0 && lx < cmd.width() && ly < cmd.height();
                if (inside && cmd instanceof WikiLayout.Cmd.Toggle toggle) {
                    if (!expanded.remove(toggle.containerId())) {
                        expanded.add(toggle.containerId());
                    }
                    relayout();
                    return true;
                }
                if (inside && cmd instanceof WikiLayout.Cmd.Image image) {
                    zoomedImage = image.path();
                    return true;
                }
            }
            String link = linkAt(mx, my);
            if (link != null) {
                openLink(link);
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar) {
            double range = Math.max(1, viewHeight() - thumbHeight());
            double t = Mth.clamp((my - contentTop - dragOffset) / range, 0, 1);
            scroll = t * maxScroll();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    
    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mx, my, button);
    }
    
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (zoomedImage != null) {
            return true;
        }
        if (mx < sideWidth && my >= sidebarTop()) {
            sideScroll = Mth.clamp(sideScroll - scrollY * ROW_HEIGHT * 2, 0, maxSideScroll());
            return true;
        }
        scroll = Mth.clamp(scroll - scrollY * 24, 0, maxScroll());
        return true;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (zoomedImage != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            zoomedImage = null;
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (hasAltDown() && keyCode == GLFW.GLFW_KEY_LEFT) {
            goBack();
            return true;
        }
        if (hasAltDown() && keyCode == GLFW.GLFW_KEY_RIGHT) {
            goForward();
            return true;
        }
        if (searchBox.isFocused()) {
            return false;
        }
        
        double page = viewHeight() * 0.9;
        switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll += page;
            case GLFW.GLFW_KEY_PAGE_UP -> scroll -= page;
            case GLFW.GLFW_KEY_DOWN -> scroll += 20;
            case GLFW.GLFW_KEY_UP -> scroll -= 20;
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = maxScroll();
            default -> {
                return false;
            }
        }
        clampScroll();
        return true;
    }
    
    @Override
    public void removed() {
        for (ResourceLocation location : configuredTextures) {
            minecraft.getTextureManager().release(location);
        }
        configuredTextures.clear();
        WikiRemoteImages.releaseTextures();
        super.removed();
    }
    
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

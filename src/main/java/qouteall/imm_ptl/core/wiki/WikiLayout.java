package qouteall.imm_ptl.core.wiki;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns a parsed page into draw commands at a given width.
 * An instance accumulates the commands of one layout call, so each call needs a new instance.
 * All coordinates are relative to the top left corner of the content area before scrolling.
 * A fill that is a background must be inserted before the commands that draw above it.
 */
@Environment(EnvType.CLIENT)
public class WikiLayout {
    public sealed interface Cmd {
        int x();
        
        int y();
        
        int width();
        
        int height();
        
        record Text(
            int x, int y, int width, int height, float scale, FormattedCharSequence text, int color
        ) implements Cmd {}
        
        record Fill(int x, int y, int width, int height, int color) implements Cmd {}
        
        record Image(
            int x, int y, int width, int height, String path, int texWidth, int texHeight
        ) implements Cmd {}
        
        record Toggle(int x, int y, int width, int height, int containerId) implements Cmd {}
    }
    
    public record Result(List<Cmd> cmds, int height, Map<String, Integer> anchors) {}
    
    public static final int TEXT_COLOR = 0xFFE6E6E6;
    public static final int HEADING_COLOR = 0xFFFFFFFF;
    public static final int LINK_COLOR = 0xFF5CB3FF;
    public static final int CODE_COLOR = 0xFFFFC66D;
    public static final int DIM_COLOR = 0xFF909090;
    private static final int CODE_BLOCK_COLOR = 0xFFD8D8D8;
    private static final int QUOTE_COLOR = 0xFFB4B4B4;
    private static final int GRID_COLOR = 0x60FFFFFF;
    private static final int BLOCK_GAP = 6;
    
    private final Font font;
    private final WikiLibrary library;
    private final Set<Integer> expanded;
    private final double guiScale;
    private final List<Cmd> cmds = new ArrayList<>();
    private final Map<String, Integer> anchors = new HashMap<>();
    private int listDepth = 0;
    
    public WikiLayout(Font font, WikiLibrary library, Set<Integer> expanded, double guiScale) {
        this.font = font;
        this.library = library;
        this.expanded = expanded;
        this.guiScale = guiScale;
    }
    
    public Result layout(WikiDocument doc, int width) {
        int y = 0;
        
        if (doc.outdated()) {
            int bannerIndex = cmds.size();
            int end = flushRun(Component.translatable("imm_ptl.wiki.outdated"), 6, width - 12, 5, 0xFFFFB0B0, 1f);
            cmds.add(bannerIndex, new Cmd.Fill(0, 0, width, end + 5, 0x60AA2222));
            y = end + 5 + BLOCK_GAP;
        }
        
        y = layoutBlocks(doc.blocks(), 0, width, y, TEXT_COLOR);
        
        y += 10;
        cmds.add(new Cmd.Fill(0, y, width, 1, 0x40FFFFFF));
        y += 6;
        if (!doc.updated().isEmpty()) {
            y = flushRun(Component.translatable("imm_ptl.wiki.updated", doc.updated()), 0, width, y, DIM_COLOR, 1f) + 2;
        }
        if (!doc.source().isEmpty()) {
            WikiSpan sourceSpan = new WikiSpan(doc.source(), false, false, false, false, doc.source(), null);
            Component line = Component.translatable("imm_ptl.wiki.source").append(" ").append(toComponent(sourceSpan, false));
            y = flushRun(line, 0, width, y, DIM_COLOR, 1f) + 2;
        }
        
        return new Result(cmds, y + 6, anchors);
    }
    
    private int layoutBlocks(List<WikiBlock> blocks, int x, int w, int y, int color) {
        return layoutBlocks(blocks, x, w, y, color, BLOCK_GAP);
    }
    
    private int layoutBlocks(List<WikiBlock> blocks, int x, int w, int y, int color, int gap) {
        boolean first = true;
        for (WikiBlock block : blocks) {
            if (!first) {
                y += gap + (block instanceof WikiBlock.Heading ? 4 : 0);
            }
            first = false;
            y = layoutBlock(block, x, w, y, color);
        }
        return y;
    }
    
    private int layoutBlock(WikiBlock block, int x, int w, int y, int color) {
        return switch (block) {
            case WikiBlock.Heading h -> layoutHeading(h, x, w, y);
            case WikiBlock.Paragraph p -> layoutInline(p.spans(), x, w, y, color, 1f, false);
            case WikiBlock.ListBlock l -> layoutList(l, x, w, y, color);
            case WikiBlock.CodeBlock c -> layoutCode(c, x, w, y);
            case WikiBlock.Quote q -> layoutQuote(q, x, w, y);
            case WikiBlock.Table t -> layoutTable(t, x, w, y, color);
            case WikiBlock.Rule ignored -> {
                cmds.add(new Cmd.Fill(x, y + 3, w, 1, 0x66FFFFFF));
                yield y + 7;
            }
            case WikiBlock.Image i -> layoutImage(i.path(), i.alt(), x, w, y);
            case WikiBlock.Container c -> layoutContainer(c, x, w, y, color);
        };
    }
    
    private int layoutHeading(WikiBlock.Heading h, int x, int w, int y) {
        float scale = switch (h.level()) {
            case 1 -> 1.8f;
            case 2 -> 1.5f;
            case 3 -> 1.25f;
            default -> 1f;
        };
        anchors.putIfAbsent(h.id(), Math.max(0, y - 2));
        int end = layoutInline(h.spans(), x, w, y, HEADING_COLOR, scale, true);
        if (h.level() <= 2) {
            cmds.add(new Cmd.Fill(x, end + 1, w, 1, 0x55FFFFFF));
            end += 3;
        }
        return end;
    }
    
    /**
     * Lays out a run of spans. Images split the run because they are drawn on their own row.
     */
    private int layoutInline(
        List<WikiSpan> spans, int x, int w, int y, int color, float scale, boolean forceBold
    ) {
        MutableComponent run = Component.empty();
        boolean hasText = false;
        for (WikiSpan span : spans) {
            if (span.imagePath() != null) {
                if (hasText) {
                    y = flushRun(run, x, w, y, color, scale);
                    run = Component.empty();
                    hasText = false;
                }
                y = layoutImage(span.imagePath(), span.text(), x, w, y) + 2;
                continue;
            }
            run.append(toComponent(span, forceBold));
            hasText = true;
        }
        if (hasText) {
            y = flushRun(run, x, w, y, color, scale);
        }
        return y;
    }
    
    private int flushRun(Component run, int x, int w, int y, int color, float scale) {
        int wrapWidth = Math.max(8, (int) (w / scale));
        int lineHeight = Math.round((font.lineHeight + 1) * scale);
        for (FormattedCharSequence line : font.split(run, wrapWidth)) {
            int lineWidth = (int) Math.ceil(font.width(line) * scale);
            cmds.add(new Cmd.Text(x, y, lineWidth, lineHeight, scale, line, color));
            y += lineHeight;
        }
        return y;
    }
    
    private Component toComponent(WikiSpan span, boolean forceBold) {
        if (span.imagePath() != null) {
            Style placeholder = Style.EMPTY.withColor(DIM_COLOR);
            if (span.link() != null) {
                placeholder = placeholder.withColor(LINK_COLOR)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(span.link())));
            }
            return Component.literal("[" + (span.text().isBlank() ? "image" : span.text()) + "]")
                .withStyle(placeholder);
        }
        Style style = Style.EMPTY;
        if (span.bold() || forceBold) {
            style = style.withBold(true);
        }
        if (span.italic()) {
            style = style.withItalic(true);
        }
        if (span.strike()) {
            style = style.withStrikethrough(true);
        }
        if (span.code()) {
            style = style.withColor(CODE_COLOR);
        }
        if (span.link() != null) {
            style = style.withColor(LINK_COLOR)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(span.link())));
        }
        return Component.literal(span.text()).withStyle(style);
    }
    
    private MutableComponent toComponent(List<WikiSpan> spans, boolean forceBold) {
        MutableComponent run = Component.empty();
        for (WikiSpan span : spans) {
            run.append(toComponent(span, forceBold));
        }
        return run;
    }
    
    private int layoutList(WikiBlock.ListBlock list, int x, int w, int y, int color) {
        int number = list.start();
        boolean first = true;
        listDepth++;
        for (List<WikiBlock> item : list.items()) {
            if (!first) {
                y += 2;
            }
            first = false;
            
            String marker = list.ordered() ? (number++) + "." : (listDepth == 1 ? "\u2022" : "-");
            int markerWidth = font.width(marker);
            int indent = Math.max(10, markerWidth + 5);
            int itemTop = y;
            int end = layoutBlocks(item, x + indent, w - indent, y, color, 2);
            cmds.add(new Cmd.Text(
                x + indent - 4 - markerWidth, itemTop, markerWidth, font.lineHeight + 1, 1f,
                Component.literal(marker).getVisualOrderText(), color
            ));
            y = Math.max(end, itemTop + font.lineHeight + 1);
        }
        listDepth--;
        return y;
    }
    
    private int layoutCode(WikiBlock.CodeBlock code, int x, int w, int y) {
        int pad = 4;
        int lineHeight = font.lineHeight + 1;
        Style style = Style.EMPTY.withColor(CODE_BLOCK_COLOR);
        
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String raw : code.lines()) {
            String s = raw.replace("\t", "    ");
            List<FormattedCharSequence> split = s.isEmpty()
                ? List.of() : font.split(Component.literal(s).withStyle(style), w - pad * 2);
            if (split.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            }
            else {
                lines.addAll(split);
            }
        }
        
        int headerHeight = code.language().isEmpty() ? 0 : lineHeight + 2;
        int height = headerHeight + lines.size() * lineHeight + pad * 2;
        cmds.add(new Cmd.Fill(x, y, w, height, 0x99000000));
        if (headerHeight > 0) {
            cmds.add(new Cmd.Fill(x, y, w, headerHeight, 0x30FFFFFF));
            cmds.add(new Cmd.Text(
                x + pad, y + 2, font.width(code.language()), lineHeight, 1f,
                Component.literal(code.language()).getVisualOrderText(), DIM_COLOR
            ));
        }
        
        int ty = y + headerHeight + pad;
        for (FormattedCharSequence line : lines) {
            cmds.add(new Cmd.Text(x + pad, ty, font.width(line), lineHeight, 1f, line, CODE_BLOCK_COLOR));
            ty += lineHeight;
        }
        return y + height;
    }
    
    private int layoutQuote(WikiBlock.Quote quote, int x, int w, int y) {
        int end = layoutBlocks(quote.blocks(), x + 9, w - 9, y, QUOTE_COLOR, 4);
        cmds.add(new Cmd.Fill(x + 2, y, 2, Math.max(1, end - y), 0xFF7A7A7A));
        return end;
    }
    
    private int layoutTable(WikiBlock.Table table, int x, int w, int y, int color) {
        int columns = table.header().size();
        int pad = 3;
        int lineHeight = font.lineHeight + 1;
        int rowCount = table.rows().size();
        
        Component[] headerCells = new Component[columns];
        Component[][] bodyCells = new Component[rowCount][columns];
        int[] columnWidths = new int[columns];
        for (int c = 0; c < columns; c++) {
            headerCells[c] = toComponent(table.header().get(c), true);
            columnWidths[c] = font.width(headerCells[c]);
        }
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < columns; c++) {
                bodyCells[r][c] = toComponent(table.rows().get(r).get(c), false);
                columnWidths[c] = Math.max(columnWidths[c], font.width(bodyCells[r][c]));
            }
        }
        
        int total = 0;
        for (int c = 0; c < columns; c++) {
            columnWidths[c] += pad * 2;
            total += columnWidths[c];
        }
        while (total > w) {
            int widest = 0;
            for (int c = 1; c < columns; c++) {
                if (columnWidths[c] > columnWidths[widest]) {
                    widest = c;
                }
            }
            if (columnWidths[widest] <= 16) {
                break;
            }
            columnWidths[widest]--;
            total--;
        }
        
        List<Integer> rowTops = new ArrayList<>();
        int rowY = y;
        for (int r = -1; r < rowCount; r++) {
            Component[] cells = r < 0 ? headerCells : bodyCells[r];
            List<List<FormattedCharSequence>> cellLines = new ArrayList<>();
            int maxLines = 1;
            for (int c = 0; c < columns; c++) {
                List<FormattedCharSequence> lines = font.split(cells[c], Math.max(8, columnWidths[c] - pad * 2));
                cellLines.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }
            
            int rowHeight = maxLines * lineHeight + pad * 2;
            rowTops.add(rowY);
            if (r < 0) {
                cmds.add(new Cmd.Fill(x, rowY, total, rowHeight, 0x30FFFFFF));
            }
            
            int cellX = x;
            for (int c = 0; c < columns; c++) {
                int innerWidth = columnWidths[c] - pad * 2;
                int ty = rowY + pad;
                for (FormattedCharSequence line : cellLines.get(c)) {
                    int lineWidth = font.width(line);
                    int offset = switch (table.aligns().get(c)) {
                        case LEFT -> 0;
                        case CENTER -> (innerWidth - lineWidth) / 2;
                        case RIGHT -> innerWidth - lineWidth;
                    };
                    cmds.add(new Cmd.Text(cellX + pad + Math.max(0, offset), ty, lineWidth, lineHeight, 1f, line, color));
                    ty += lineHeight;
                }
                cellX += columnWidths[c];
            }
            rowY += rowHeight;
        }
        
        for (int top : rowTops) {
            cmds.add(new Cmd.Fill(x, top, total + 1, 1, GRID_COLOR));
        }
        cmds.add(new Cmd.Fill(x, rowY, total + 1, 1, GRID_COLOR));
        int lineX = x;
        for (int c = 0; c < columns; c++) {
            cmds.add(new Cmd.Fill(lineX, y, 1, rowY - y, GRID_COLOR));
            lineX += columnWidths[c];
        }
        cmds.add(new Cmd.Fill(lineX, y, 1, rowY - y, GRID_COLOR));
        return rowY + 1;
    }
    
    /**
     * Small images keep their pixel size and large ones shrink to the available width.
     */
    private int layoutImage(String path, String alt, int x, int w, int y) {
        int[] size = library.getImageSize(path);
        if (size == null || size[0] <= 0 || size[1] <= 0) {
            boolean remote = path.startsWith("http");
            String label = alt.isBlank() ? "image" : alt;
            if (!remote) {
                String key = WikiRemoteImages.isPending(path)
                    ? "imm_ptl.wiki.image_loading" : "imm_ptl.wiki.image_unavailable";
                label = I18n.get(key, label);
            }
            WikiSpan placeholder = new WikiSpan(label, false, false, false, false, remote ? path : null, path);
            return flushRun(toComponent(placeholder, false), x, w, y, DIM_COLOR, 1f);
        }
        
        int drawWidth = (int) Math.max(1, Math.min(w, Math.round(size[0] / guiScale)));
        int drawHeight = Math.max(1, (int) Math.round((double) size[1] * drawWidth / size[0]));
        cmds.add(new Cmd.Image(x + (w - drawWidth) / 2, y, drawWidth, drawHeight, path, size[0], size[1]));
        return y + drawHeight;
    }
    
    private int layoutContainer(WikiBlock.Container container, int x, int w, int y, int color) {
        boolean details = container.kind().equals("details");
        String title = container.title();
        if (title.isEmpty()) {
            String key = "imm_ptl.wiki.container." + (details ? "details" : container.kind());
            title = I18n.exists(key) ? I18n.get(key) : container.kind().toUpperCase(Locale.ROOT);
        }
        
        if (details) {
            boolean open = expanded.contains(container.id());
            Component label = Component.literal((open ? "\u25BC " : "\u25B6 ") + title)
                .withStyle(Style.EMPTY.withBold(true));
            int fillIndex = cmds.size();
            int labelEnd = flushRun(label, x + 5, w - 10, y + 4, TEXT_COLOR, 1f);
            int headerHeight = Math.max(font.lineHeight + 8, labelEnd - y + 4);
            cmds.add(fillIndex, new Cmd.Fill(x, y, w, headerHeight, 0x30FFFFFF));
            cmds.add(new Cmd.Toggle(x, y, w, headerHeight, container.id()));
            y += headerHeight;
            if (!open) {
                return y;
            }
            int end = layoutBlocks(container.blocks(), x + 10, w - 14, y + 5, color, BLOCK_GAP) + 5;
            cmds.add(new Cmd.Fill(x + 2, y, 2, end - y, 0x55FFFFFF));
            return end;
        }
        
        int accent = accentColor(container.kind());
        int backgroundIndex = cmds.size();
        int inner = y + 5;
        if (!title.isEmpty()) {
            Component label = Component.literal(title)
                .withStyle(Style.EMPTY.withBold(true).withColor(accent & 0xFFFFFF));
            inner = flushRun(label, x + 10, w - 14, inner, accent, 1f) + 3;
        }
        int end = layoutBlocks(container.blocks(), x + 10, w - 14, inner, color, 4) + 5;
        cmds.add(backgroundIndex, new Cmd.Fill(x, y, w, end - y, (accent & 0xFFFFFF) | 0x25000000));
        cmds.add(backgroundIndex + 1, new Cmd.Fill(x, y, 3, end - y, accent));
        return end;
    }
    
    private static int accentColor(String kind) {
        return switch (kind) {
            case "tip" -> 0xFF42B983;
            case "warning" -> 0xFFE7C000;
            case "danger" -> 0xFFCC4444;
            case "info", "note" -> 0xFF4F9DE8;
            default -> 0xFF909090;
        };
    }
}

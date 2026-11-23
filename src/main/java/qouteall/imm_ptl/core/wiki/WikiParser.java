package qouteall.imm_ptl.core.wiki;

import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Markdown dialect described in wiki/_FORMAT.md.
 */
public final class WikiParser {
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})(?:\\s+(.*))?$");
    private static final Pattern HEADING_CLOSE = Pattern.compile("\\s+#+\\s*$");
    private static final Pattern HEADING_ID = Pattern.compile("\\s*\\{#([^}\\s]+)}\\s*$");
    private static final Pattern RULE = Pattern.compile("^ {0,3}([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*)([-*+]|\\d{1,9}[.)])(\\s+)(.*)$");
    private static final Pattern TABLE_SEPARATOR =
        Pattern.compile("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$");
    private static final Pattern CONTAINER = Pattern.compile("^:::\\s*(\\w+)?\\s*(.*)$");
    
    private final Map<String, Integer> idCounts = new HashMap<>();
    private final Map<String, Integer> anchorContainers = new HashMap<>();
    private final StringBuilder searchText = new StringBuilder();
    private int nextContainerId = 0;
    private int currentContainer = -1;
    
    private WikiParser() {}
    
    public static WikiDocument parse(String source) {
        String text = source.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        
        Map<String, String> meta = new LinkedHashMap<>();
        int bodyStart = 0;
        if (!lines.isEmpty() && lines.get(0).strip().equals("---")) {
            int end = -1;
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).strip().equals("---")) {
                    end = i;
                    break;
                }
            }
            if (end > 0) {
                for (int i = 1; i < end; i++) {
                    parseMetaLine(lines.get(i), meta);
                }
                bodyStart = end + 1;
            }
        }
        
        List<String> body = new ArrayList<>(lines.subList(bodyStart, lines.size()));
        body.replaceAll(WikiParser::expandLeadingTabs);
        
        WikiParser parser = new WikiParser();
        List<WikiBlock> blocks = parser.parseBlocks(body);
        return new WikiDocument(
            meta, blocks, parser.anchorContainers,
            parser.searchText.toString().toLowerCase(Locale.ROOT)
        );
    }
    
    private static void parseMetaLine(String line, Map<String, String> meta) {
        int colon = line.indexOf(':');
        if (colon <= 0) {
            return;
        }
        String key = line.substring(0, colon).strip();
        String value = line.substring(colon + 1).strip();
        if (value.startsWith("\"")) {
            try {
                value = JsonParser.parseString(value).getAsString();
            }
            catch (RuntimeException ignored) {
                // Keeps the raw value when it is not valid JSON.
            }
        }
        meta.put(key, value);
    }
    
    private static String expandLeadingTabs(String line) {
        int tabs = 0;
        while (tabs < line.length() && line.charAt(tabs) == '\t') {
            tabs++;
        }
        return tabs == 0 ? line : "    ".repeat(tabs) + line.substring(tabs);
    }
    
    private List<WikiBlock> parseBlocks(List<String> lines) {
        List<WikiBlock> out = new ArrayList<>();
        int size = lines.size();
        int i = 0;
        while (i < size) {
            String line = lines.get(i);
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                i++;
                continue;
            }
            
            if (isFence(trimmed)) {
                i = parseFence(lines, i, out);
                continue;
            }
            
            Matcher container = CONTAINER.matcher(trimmed);
            if (container.matches()) {
                if (trimmed.equals(":::")) {
                    i++;
                }
                else {
                    i = parseContainer(lines, i, container, out);
                }
                continue;
            }
            
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                out.add(parseHeading(heading));
                i++;
                continue;
            }
            
            if (RULE.matcher(line).matches()) {
                out.add(new WikiBlock.Rule());
                i++;
                continue;
            }
            
            if (trimmed.startsWith(">")) {
                i = parseQuote(lines, i, out);
                continue;
            }
            
            if (isTableStart(lines, i)) {
                i = parseTable(lines, i, out);
                continue;
            }
            
            if (LIST_ITEM.matcher(line).matches()) {
                i = parseList(lines, i, out);
                continue;
            }
            
            i = parseParagraph(lines, i, out);
        }
        return out;
    }
    
    private static boolean isFence(String trimmed) {
        return trimmed.startsWith("```") || trimmed.startsWith("~~~");
    }
    
    private boolean startsNewBlock(String line) {
        String t = line.strip();
        return isFence(t) || t.startsWith(">") || t.startsWith(":::")
            || HEADING.matcher(line).matches() || RULE.matcher(line).matches();
    }
    
    private int parseFence(List<String> lines, int i, List<WikiBlock> out) {
        String opening = lines.get(i);
        String trimmed = opening.strip();
        final char fenceChar = trimmed.charAt(0);
        int fenceLength = 0;
        while (fenceLength < trimmed.length() && trimmed.charAt(fenceLength) == fenceChar) {
            fenceLength++;
        }
        String info = trimmed.substring(fenceLength).strip();
        String language = info.isEmpty() ? "" : info.split("\\s+")[0];
        int indent = leadingSpaces(opening);
        
        List<String> code = new ArrayList<>();
        i++;
        while (i < lines.size()) {
            String l = lines.get(i);
            String lt = l.strip();
            if (lt.length() >= fenceLength && lt.chars().allMatch(ch -> ch == fenceChar)) {
                break;
            }
            code.add(stripIndent(l, indent));
            i++;
        }
        out.add(new WikiBlock.CodeBlock(language, code));
        addSearch(String.join(" ", code));
        return i + 1;
    }
    
    private int parseContainer(List<String> lines, int i, Matcher m, List<WikiBlock> out) {
        String kind = m.group(1) == null ? "" : m.group(1).toLowerCase(Locale.ROOT);
        String title = m.group(2).strip();
        List<String> inner = new ArrayList<>();
        i++;
        while (i < lines.size() && !lines.get(i).strip().equals(":::")) {
            inner.add(lines.get(i));
            i++;
        }
        
        int id = nextContainerId++;
        int previous = currentContainer;
        currentContainer = id;
        List<WikiBlock> children = parseBlocks(inner);
        currentContainer = previous;
        
        out.add(new WikiBlock.Container(id, kind, title, children));
        addSearch(title);
        return i + 1;
    }
    
    private WikiBlock.Heading parseHeading(Matcher m) {
        int level = m.group(1).length();
        String text = m.group(2) == null ? "" : m.group(2).strip();
        text = HEADING_CLOSE.matcher(text).replaceFirst("");
        
        String explicitId = null;
        Matcher idMatcher = HEADING_ID.matcher(text);
        if (idMatcher.find()) {
            explicitId = idMatcher.group(1);
            text = text.substring(0, idMatcher.start());
        }
        
        List<WikiSpan> spans = WikiInlineParser.parse(text);
        String plain = plainText(spans);
        String base = explicitId != null ? explicitId : slugify(plain);
        int count = idCounts.getOrDefault(base, 0);
        idCounts.put(base, count + 1);
        String id = count == 0 ? base : base + "-" + count;
        
        anchorContainers.put(id, currentContainer);
        addSearch(plain);
        return new WikiBlock.Heading(level, id, spans);
    }
    
    /**
     * Follows the slugify function of VuePress. A leading digit gets an underscore prefix.
     */
    static String slugify(String text) {
        String s = text.strip().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[\\s~`!@#$%^&*()\\-_+=\\[\\]{}|\\\\;:\"'<>,.?/]+", "-");
        s = s.replaceAll("-{2,}", "-").replaceAll("^-+|-+$", "");
        if (!s.isEmpty() && Character.isDigit(s.charAt(0))) {
            s = "_" + s;
        }
        return s;
    }
    
    private int parseQuote(List<String> lines, int i, List<WikiBlock> out) {
        List<String> inner = new ArrayList<>();
        while (i < lines.size() && lines.get(i).strip().startsWith(">")) {
            String l = lines.get(i).strip().substring(1);
            if (l.startsWith(" ")) {
                l = l.substring(1);
            }
            inner.add(l);
            i++;
        }
        out.add(new WikiBlock.Quote(parseBlocks(inner)));
        return i;
    }
    
    private boolean isTableStart(List<String> lines, int i) {
        if (i + 1 >= lines.size()) {
            return false;
        }
        String next = lines.get(i + 1);
        return lines.get(i).indexOf('|') >= 0 && next.indexOf('|') >= 0
            && TABLE_SEPARATOR.matcher(next).matches();
    }
    
    private int parseTable(List<String> lines, int i, List<WikiBlock> out) {
        List<String> headerCells = splitRow(lines.get(i));
        List<String> separatorCells = splitRow(lines.get(i + 1));
        int columns = headerCells.size();
        
        List<WikiBlock.Align> aligns = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            String sep = c < separatorCells.size() ? separatorCells.get(c) : "";
            boolean left = sep.startsWith(":");
            boolean right = sep.endsWith(":");
            aligns.add(left && right ? WikiBlock.Align.CENTER : right ? WikiBlock.Align.RIGHT : WikiBlock.Align.LEFT);
        }
        
        List<List<WikiSpan>> header = new ArrayList<>();
        for (String cell : headerCells) {
            List<WikiSpan> spans = WikiInlineParser.parse(cell);
            header.add(spans);
            addSearch(plainText(spans));
        }
        
        List<List<List<WikiSpan>>> rows = new ArrayList<>();
        i += 2;
        while (i < lines.size() && !lines.get(i).isBlank() && lines.get(i).indexOf('|') >= 0) {
            List<String> cells = splitRow(lines.get(i));
            List<List<WikiSpan>> row = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                List<WikiSpan> spans = WikiInlineParser.parse(c < cells.size() ? cells.get(c) : "");
                row.add(spans);
                addSearch(plainText(spans));
            }
            rows.add(row);
            i++;
        }
        out.add(new WikiBlock.Table(header, aligns, rows));
        return i;
    }
    
    /**
     * Splits a table row on pipes that are neither escaped nor inside inline code.
     */
    private static List<String> splitRow(String line) {
        String s = line.strip();
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        if (s.endsWith("|") && !s.endsWith("\\|")) {
            s = s.substring(0, s.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inCode = false;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\\' && k + 1 < s.length() && s.charAt(k + 1) == '|') {
                cell.append('|');
                k++;
            }
            else if (c == '`') {
                inCode = !inCode;
                cell.append(c);
            }
            else if (c == '|' && !inCode) {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            }
            else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().strip());
        return cells;
    }
    
    private int parseList(List<String> lines, int i, List<WikiBlock> out) {
        int size = lines.size();
        Matcher first = LIST_ITEM.matcher(lines.get(i));
        first.matches();
        int baseIndent = first.group(1).length();
        boolean ordered = Character.isDigit(first.group(2).charAt(0));
        int start = ordered ? Integer.parseInt(first.group(2).replaceAll("\\D", "")) : 1;
        List<List<WikiBlock>> items = new ArrayList<>();
        
        while (i < size) {
            Matcher m = LIST_ITEM.matcher(lines.get(i));
            if (!m.matches() || m.group(1).length() != baseIndent
                || Character.isDigit(m.group(2).charAt(0)) != ordered
            ) {
                break;
            }
            int contentIndent = baseIndent + m.group(2).length() + Math.min(m.group(3).length(), 4);
            List<String> itemLines = new ArrayList<>();
            itemLines.add(m.group(4));
            i++;
            
            while (i < size) {
                String l = lines.get(i);
                if (l.isBlank()) {
                    int j = i;
                    while (j < size && lines.get(j).isBlank()) {
                        j++;
                    }
                    if (j < size && leadingSpaces(lines.get(j)) > baseIndent) {
                        for (int k = i; k < j; k++) {
                            itemLines.add("");
                        }
                        i = j;
                        continue;
                    }
                    i = j;
                    break;
                }
                Matcher next = LIST_ITEM.matcher(l);
                if (next.matches() && next.group(1).length() <= baseIndent) {
                    break;
                }
                if (leadingSpaces(l) > baseIndent) {
                    itemLines.add(stripIndent(l, contentIndent));
                    i++;
                    continue;
                }
                if (startsNewBlock(l)) {
                    break;
                }
                itemLines.add(l.strip());
                i++;
            }
            items.add(parseBlocks(itemLines));
        }
        out.add(new WikiBlock.ListBlock(ordered, start, items));
        return i;
    }
    
    private int parseParagraph(List<String> lines, int i, List<WikiBlock> out) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        boolean previousHardBreak = false;
        while (i < lines.size()) {
            String l = lines.get(i);
            if (l.isBlank()) {
                break;
            }
            if (!first && (startsNewBlock(l) || LIST_ITEM.matcher(l).matches() || isTableStart(lines, i))) {
                break;
            }
            if (!first) {
                sb.append(previousHardBreak ? '\n' : ' ');
            }
            sb.append(l.strip());
            previousHardBreak = l.endsWith("  ");
            first = false;
            i++;
        }
        
        List<WikiSpan> spans = WikiInlineParser.parse(sb.toString());
        addSearch(plainText(spans));
        List<WikiSpan> meaningful = spans.stream()
            .filter(s -> s.imagePath() != null || !s.text().isBlank())
            .toList();
        if (meaningful.size() == 1 && meaningful.get(0).imagePath() != null) {
            WikiSpan image = meaningful.get(0);
            out.add(new WikiBlock.Image(image.imagePath(), image.text()));
        }
        else if (!meaningful.isEmpty()) {
            out.add(new WikiBlock.Paragraph(spans));
        }
        return i;
    }
    
    private void addSearch(String text) {
        searchText.append(text).append(' ');
    }
    
    static String plainText(List<WikiSpan> spans) {
        StringBuilder sb = new StringBuilder();
        for (WikiSpan span : spans) {
            sb.append(span.text());
        }
        return sb.toString();
    }
    
    private static int leadingSpaces(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') {
            n++;
        }
        return n;
    }
    
    private static String stripIndent(String s, int max) {
        return s.substring(Math.min(leadingSpaces(s), max));
    }
}

package qouteall.imm_ptl.core.wiki;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the inline syntax: bold, italic, strike, code, links, images and escapes.
 * Underscore emphasis is not supported because identifiers such as set_portal_size are common in prose.
 */
public final class WikiInlineParser {
    private static final String PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
    
    private record Format(boolean bold, boolean italic, boolean strike, @Nullable String link) {
        Format withBold() {
            return new Format(true, italic, strike, link);
        }
        
        Format withItalic() {
            return new Format(bold, true, strike, link);
        }
        
        Format withStrike() {
            return new Format(bold, italic, true, link);
        }
        
        Format withLink(String newLink) {
            return new Format(bold, italic, strike, newLink);
        }
    }
    
    private record LinkMatch(String text, String destination, int end) {}
    
    private WikiInlineParser() {}
    
    public static List<WikiSpan> parse(String text) {
        List<WikiSpan> out = new ArrayList<>();
        parseInto(text, new Format(false, false, false, null), out);
        return merge(out);
    }
    
    private static void parseInto(String s, Format f, List<WikiSpan> out) {
        StringBuilder buf = new StringBuilder();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            
            if (c == '\\' && i + 1 < n && PUNCTUATION.indexOf(s.charAt(i + 1)) >= 0) {
                buf.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            
            if (c == '`') {
                int run = countRun(s, i, '`');
                int close = findBacktickClose(s, i + run, run);
                if (close >= 0) {
                    flush(buf, f, out);
                    String code = s.substring(i + run, close);
                    if (code.length() > 2 && code.startsWith(" ") && code.endsWith(" ") && !code.isBlank()) {
                        code = code.substring(1, code.length() - 1);
                    }
                    out.add(new WikiSpan(code, f.bold, f.italic, f.strike, true, f.link, null));
                    i = close + run;
                }
                else {
                    buf.append("`".repeat(run));
                    i += run;
                }
                continue;
            }
            
            if (c == '!' && i + 1 < n && s.charAt(i + 1) == '[') {
                LinkMatch image = parseLinkAt(s, i + 1);
                if (image != null) {
                    flush(buf, f, out);
                    out.add(new WikiSpan(image.text, false, false, false, false, null, image.destination));
                    i = image.end;
                    continue;
                }
            }
            
            if (c == '[') {
                LinkMatch link = parseLinkAt(s, i);
                if (link != null) {
                    flush(buf, f, out);
                    parseInto(link.text, f.withLink(link.destination), out);
                    i = link.end;
                    continue;
                }
            }
            
            if (c == '<') {
                if (s.regionMatches(true, i, "<br>", 0, 4)) {
                    buf.append('\n');
                    i += 4;
                    continue;
                }
                if (s.regionMatches(true, i, "<br/>", 0, 5)) {
                    buf.append('\n');
                    i += 5;
                    continue;
                }
                if (s.regionMatches(true, i, "<br />", 0, 6)) {
                    buf.append('\n');
                    i += 6;
                    continue;
                }
                if (s.startsWith("<http", i)) {
                    int gt = s.indexOf('>', i);
                    if (gt > 0 && s.substring(i, gt).chars().noneMatch(Character::isWhitespace)) {
                        flush(buf, f, out);
                        String url = s.substring(i + 1, gt);
                        out.add(new WikiSpan(url, f.bold, f.italic, f.strike, false, url, null));
                        i = gt + 1;
                        continue;
                    }
                }
            }
            
            if (c == '*' || c == '~') {
                int run = countRun(s, i, c);
                int need = c == '~' ? (run >= 2 ? 2 : 0) : Math.min(run, 3);
                if (need > 0 && i + need < n && !Character.isWhitespace(s.charAt(i + need))) {
                    int close = findCloser(s, i + need, c, need);
                    if (close >= 0) {
                        flush(buf, f, out);
                        Format inner;
                        if (c == '~') {
                            inner = f.withStrike();
                        }
                        else if (need == 1) {
                            inner = f.withItalic();
                        }
                        else if (need == 2) {
                            inner = f.withBold();
                        }
                        else {
                            inner = f.withBold().withItalic();
                        }
                        parseInto(s.substring(i + need, close), inner, out);
                        i = close + need;
                        continue;
                    }
                }
                buf.append(String.valueOf(c).repeat(run));
                i += run;
                continue;
            }
            
            if (c == 'h' && f.link == null
                && (i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1)))
                && (s.startsWith("https://", i) || s.startsWith("http://", i))
            ) {
                int j = i;
                while (j < n && !Character.isWhitespace(s.charAt(j)) && s.charAt(j) != '<') {
                    j++;
                }
                while (j > i && ".,;:!?)\"'".indexOf(s.charAt(j - 1)) >= 0) {
                    j--;
                }
                flush(buf, f, out);
                String url = s.substring(i, j);
                out.add(new WikiSpan(url, f.bold, f.italic, f.strike, false, url, null));
                i = j;
                continue;
            }
            
            buf.append(c);
            i++;
        }
        flush(buf, f, out);
    }
    
    private static void flush(StringBuilder buf, Format f, List<WikiSpan> out) {
        if (buf.length() > 0) {
            out.add(new WikiSpan(buf.toString(), f.bold, f.italic, f.strike, false, f.link, null));
            buf.setLength(0);
        }
    }
    
    private static int countRun(String s, int from, char c) {
        int j = from;
        while (j < s.length() && s.charAt(j) == c) {
            j++;
        }
        return j - from;
    }
    
    /**
     * Returns the index of the closing backtick run with exactly the same length, or -1.
     */
    private static int findBacktickClose(String s, int from, int runLength) {
        int j = from;
        while (j < s.length()) {
            if (s.charAt(j) == '`') {
                int run = countRun(s, j, '`');
                if (run == runLength) {
                    return j;
                }
                j += run;
            }
            else {
                j++;
            }
        }
        return -1;
    }
    
    /**
     * Finds the delimiter run that closes an emphasis opened with {@code need} characters.
     * A single-character italic closer skips doubled runs, which are nested bold.
     */
    private static int findCloser(String s, int from, char c, int need) {
        int n = s.length();
        int j = from;
        while (j < n) {
            char d = s.charAt(j);
            if (d == '\\') {
                j += 2;
                continue;
            }
            if (d == '`') {
                int run = countRun(s, j, '`');
                int end = findBacktickClose(s, j + run, run);
                j = end >= 0 ? end + run : j + run;
                continue;
            }
            if (d == c) {
                int run = countRun(s, j, c);
                boolean afterText = j > from && !Character.isWhitespace(s.charAt(j - 1));
                if (afterText) {
                    if (need == 1 && run == 1) {
                        return j;
                    }
                    if (need >= 2 && run >= need) {
                        return j;
                    }
                }
                j += run;
                continue;
            }
            j++;
        }
        return -1;
    }
    
    /**
     * Parses {@code [text](destination "title")} where {@code start} is the index of the opening bracket.
     */
    @Nullable
    private static LinkMatch parseLinkAt(String s, int start) {
        int n = s.length();
        int depth = 0;
        int close = -1;
        int i = start;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '`') {
                int run = countRun(s, i, '`');
                int end = findBacktickClose(s, i + run, run);
                i = end >= 0 ? end + run : i + run;
                continue;
            }
            if (c == '[') {
                depth++;
            }
            else if (c == ']') {
                depth--;
                if (depth == 0) {
                    close = i;
                    break;
                }
            }
            i++;
        }
        if (close < 0 || close + 1 >= n || s.charAt(close + 1) != '(') {
            return null;
        }
        
        int p = close + 2;
        while (p < n && s.charAt(p) == ' ') {
            p++;
        }
        String destination;
        if (p < n && s.charAt(p) == '<') {
            int gt = s.indexOf('>', p);
            if (gt < 0) {
                return null;
            }
            destination = s.substring(p + 1, gt);
            p = gt + 1;
        }
        else {
            int destStart = p;
            int parens = 0;
            while (p < n) {
                char c = s.charAt(p);
                if (c == '\\' && p + 1 < n) {
                    p += 2;
                    continue;
                }
                if (Character.isWhitespace(c)) {
                    break;
                }
                if (c == '(') {
                    parens++;
                }
                else if (c == ')') {
                    if (parens == 0) {
                        break;
                    }
                    parens--;
                }
                p++;
            }
            destination = unescape(s.substring(destStart, Math.min(p, n)));
        }
        while (p < n && s.charAt(p) == ' ') {
            p++;
        }
        if (p < n && (s.charAt(p) == '"' || s.charAt(p) == '\'')) {
            int endQuote = s.indexOf(s.charAt(p), p + 1);
            if (endQuote < 0) {
                return null;
            }
            p = endQuote + 1;
            while (p < n && s.charAt(p) == ' ') {
                p++;
            }
        }
        if (p >= n || s.charAt(p) != ')') {
            return null;
        }
        return new LinkMatch(s.substring(start + 1, close), destination, p + 1);
    }
    
    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && PUNCTUATION.indexOf(s.charAt(i + 1)) >= 0) {
                sb.append(s.charAt(i + 1));
                i++;
            }
            else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    private static List<WikiSpan> merge(List<WikiSpan> spans) {
        List<WikiSpan> merged = new ArrayList<>();
        for (WikiSpan span : spans) {
            if (!merged.isEmpty()) {
                WikiSpan last = merged.get(merged.size() - 1);
                if (last.imagePath() == null && span.imagePath() == null && last.sameStyle(span)) {
                    merged.set(merged.size() - 1, new WikiSpan(
                        last.text() + span.text(),
                        last.bold(), last.italic(), last.strike(), last.code(), last.link(), null
                    ));
                    continue;
                }
            }
            merged.add(span);
        }
        return merged;
    }
}

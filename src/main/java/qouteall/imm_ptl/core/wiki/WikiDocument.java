package qouteall.imm_ptl.core.wiki;

import java.util.List;
import java.util.Map;

/**
 * A parsed wiki page.
 * The anchor map goes from a heading id to the id of the container holding it, or -1 for no container.
 * The search text is lowercase.
 */
public record WikiDocument(
    Map<String, String> meta,
    List<WikiBlock> blocks,
    Map<String, Integer> anchorContainers,
    String searchText
) {
    public String title(String fallback) {
        return meta.getOrDefault("title", fallback);
    }
    
    public boolean outdated() {
        return "true".equals(meta.get("outdated"));
    }
    
    public String source() {
        return meta.getOrDefault("source", "");
    }
    
    public String updated() {
        return meta.getOrDefault("updated", "");
    }
}

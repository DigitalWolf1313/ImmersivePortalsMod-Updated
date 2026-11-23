package qouteall.imm_ptl.core.wiki;

import java.util.List;

/**
 * The block level nodes of a parsed wiki page.
 */
public sealed interface WikiBlock {
    enum Align {LEFT, CENTER, RIGHT}
    
    record Heading(int level, String id, List<WikiSpan> spans) implements WikiBlock {}
    
    record Paragraph(List<WikiSpan> spans) implements WikiBlock {}
    
    record ListBlock(boolean ordered, int start, List<List<WikiBlock>> items) implements WikiBlock {}
    
    record CodeBlock(String language, List<String> lines) implements WikiBlock {}
    
    record Quote(List<WikiBlock> blocks) implements WikiBlock {}
    
    record Table(
        List<List<WikiSpan>> header,
        List<Align> aligns,
        List<List<List<WikiSpan>>> rows
    ) implements WikiBlock {}
    
    record Rule() implements WikiBlock {}
    
    record Image(String path, String alt) implements WikiBlock {}
    
    /**
     * The id is unique inside the page and is used to remember which details are expanded.
     */
    record Container(int id, String kind, String title, List<WikiBlock> blocks) implements WikiBlock {}
}

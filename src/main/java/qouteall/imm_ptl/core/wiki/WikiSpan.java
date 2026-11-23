package qouteall.imm_ptl.core.wiki;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A piece of inline text with one set of styles.
 * An image span keeps its alt text in {@code text} and its path in {@code imagePath}.
 */
public record WikiSpan(
    String text,
    boolean bold,
    boolean italic,
    boolean strike,
    boolean code,
    @Nullable String link,
    @Nullable String imagePath
) {
    public boolean sameStyle(WikiSpan other) {
        return bold == other.bold && italic == other.italic && strike == other.strike
            && code == other.code && Objects.equals(link, other.link);
    }
}

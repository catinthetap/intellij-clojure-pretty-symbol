package zjhmale.cps;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by zjh on 2016/2/19.
 */
public class CPSFoldingBuilder implements FoldingBuilder {
    private static final Pattern symbolPattern = Pattern.compile("\\(fn|\\(partial|\\(->|\\(def|not=|#\\(|#\\{");
    private static final HashMap<String, String> prettySymbolMaps;

    static {
        prettySymbolMaps = new HashMap<String, String>();
        prettySymbolMaps.put("(fn", "λ");
        prettySymbolMaps.put("(partial", "Ƥ");
        prettySymbolMaps.put("(def", "→");
        prettySymbolMaps.put("(defn", "ƒ");
        prettySymbolMaps.put("(->", "→");
        prettySymbolMaps.put("(->>", "⇉");
        prettySymbolMaps.put("not=", "≠");
        prettySymbolMaps.put("#(", "λ(");
        prettySymbolMaps.put("#{", "∈{");
    }


    public static boolean isDelimiterMatch(String text, int start) {
        String nextChar = "";
        int leftCount = 0;
        int rightCount = 0;
        while (!nextChar.equals("\n") && start < text.length()) {
            nextChar = text.substring(start, start + 1);
            if (nextChar.equals("(")) {
                leftCount++;
            }
            if (nextChar.equals(")")) {
                rightCount++;
            }
            start++;
        }
        return rightCount > leftCount;
    }

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull final ASTNode node, @NotNull final Document document) {
        List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
        final String text = node.getText();
        final Matcher matcher = symbolPattern.matcher(text);

        while (matcher.find()) {
            final String key = text.substring(matcher.start(), matcher.end());
            final TextRange nodeRange = node.getTextRange();
            int rangeStart = nodeRange.getStartOffset() + matcher.start();
            int rangeEnd = nodeRange.getStartOffset() + matcher.end();
            String pretty = prettySymbolMaps.get(key);
            boolean shouldFold = true;
            if (key.startsWith("(")) {
                rangeStart += 1;
            }
            if (key.startsWith("(def")) {
                String nextChar = text.substring(rangeEnd, rangeEnd + 1);
                if (!nextChar.equals(" ")) {
                    shouldFold = false;
                }
                if (nextChar.equals("n")) {
                    pretty = prettySymbolMaps.get("(defn");
                    rangeEnd += 1;
                    shouldFold = true;
                }
            }
            if (key.startsWith("(->")) {
                String nextChar = text.substring(rangeEnd, rangeEnd + 1);
                if (nextChar.equals(">")) {
                    pretty = prettySymbolMaps.get("(->>");
                    rangeEnd += 1;
                }
                shouldFold = isDelimiterMatch(text, rangeStart);
            }
            if (key.equals("not=")) {
                shouldFold = isDelimiterMatch(text, rangeStart);
            }

            if (shouldFold) {
                final TextRange range = TextRange.create(rangeStart, rangeEnd);
                descriptors.add(new CPSFoldingDescriptor(node, range, null, pretty, true));
            }
        }
        return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        return null;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return true;
    }
}
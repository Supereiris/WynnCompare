package com.wynncompare.comparison;

import com.wynncompare.comparison.LoreParser.StatGroup;
import com.wynncompare.comparison.LoreParser.StatLine;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ComparisonBuilder {

    public static List<Text> build(List<StatLine> hoveredLines, List<StatLine> equippedLines) {
        List<Text> result = new ArrayList<>();
        boolean firstGroup = true;

        for (StatGroup group : StatGroup.values()) {
            if (group == StatGroup.NONE) {
                continue;
            }

            List<Text> groupLines = buildGroup(hoveredLines, equippedLines, group);

            if (groupLines.isEmpty()) {
                continue;
            }

            if (!firstGroup) {
                result.add(Text.empty());
            }
            result.addAll(groupLines);
            firstGroup = false;
        }

        return result;
    }

    private static List<Text> buildGroup(List<StatLine> hoveredLines, List<StatLine> equippedLines, StatGroup group) {
        List<Text> result = new ArrayList<>();

        // Collect hovered lines for this group
        List<StatLine> hoveredGroup = new ArrayList<>();
        for (StatLine sl : hoveredLines) {
            if (sl.group() == group) {
                hoveredGroup.add(sl);
            }
        }

        // Build label -> StatLine map for equipped lines in this group
        Map<String, StatLine> equippedMap = new LinkedHashMap<>();
        for (StatLine sl : equippedLines) {
            if (sl.group() == group) {
                equippedMap.put(sl.label(), sl);
            }
        }

        // Process hovered stats
        for (StatLine hovLine : hoveredGroup) {
            StatLine eqLine = equippedMap.remove(hovLine.label());

            if (hovLine.isTextOnly()) {
                // Presence-only: show as-is, no diff
                result.add(hovLine.originalText());
            } else if (eqLine != null && !eqLine.isTextOnly()) {
                // Both have numeric values — show diff
                double diff = hovLine.value() - eqLine.value();
                Text diffText = formatDiff(diff, hovLine.isPercent());
                MutableText combined = hovLine.originalText().copy().append(Text.literal(" ")).append(diffText);
                result.add(combined);
            } else {
                // Hovered-only or equipped is text-only — show as-is
                result.add(hovLine.originalText());
            }
        }

        // Append equipped-only stats as gray strikethrough (skip for requirements)
        if (group != StatGroup.REQUIREMENT) {
            for (StatLine eqOnly : equippedMap.values()) {
                MutableText removed = eqOnly.originalText().copy()
                        .formatted(Formatting.GRAY, Formatting.STRIKETHROUGH);
                result.add(removed);
            }
        }

        return result;
    }

    private static Text formatDiff(double diff, boolean isPercent) {
        String suffix = isPercent ? "%" : "";
        if (diff > 0) {
            String text = "(+" + formatNumber(diff) + suffix + ")";
            return Text.literal(text).formatted(Formatting.GREEN);
        } else if (diff < 0) {
            String text = "(" + formatNumber(diff) + suffix + ")";
            return Text.literal(text).formatted(Formatting.RED);
        } else {
            String text = "(+0" + suffix + ")";
            return Text.literal(text).formatted(Formatting.GRAY);
        }
    }

    private static String formatNumber(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}

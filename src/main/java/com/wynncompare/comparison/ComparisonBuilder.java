package com.wynncompare.comparison;

import com.wynncompare.comparison.LoreParser.StatGroup;
import com.wynncompare.comparison.LoreParser.StatLine;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
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
        // Collect hovered and equipped lines for this group
        List<StatLine> hoveredGroup = new ArrayList<>();
        for (StatLine sl : hoveredLines) {
            if (sl.group() == group) {
                hoveredGroup.add(sl);
            }
        }

        Map<String, StatLine> equippedMap = new LinkedHashMap<>();
        for (StatLine sl : equippedLines) {
            if (sl.group() == group) {
                equippedMap.put(statKey(sl), sl);
            }
        }

        if (group == StatGroup.ATTACK_SPEED) {
            return buildAttackSpeedGroup(hoveredGroup, equippedMap);
        }

        if (group == StatGroup.AVERAGE_DAMAGE) {
            return buildDamageGroup(hoveredGroup, equippedMap);
        }

        List<Text> result = new ArrayList<>();
        processHoveredLines(hoveredGroup, equippedMap, result);

        // Append equipped-only stats as dark gray plain text (skip for requirements)
        if (group != StatGroup.REQUIREMENT) {
            appendEquippedOnly(equippedMap, result);
        }

        return result;
    }

    private static List<Text> buildAttackSpeedGroup(List<StatLine> hoveredGroup, Map<String, StatLine> equippedMap) {
        List<Text> result = new ArrayList<>();

        for (StatLine hovLine : hoveredGroup) {
            StatLine eqLine = equippedMap.remove(statKey(hovLine));

            if (eqLine != null) {
                // value stores the tier index (0=Super Slow, 6=Super Fast)
                int diff = (int) (hovLine.value() - eqLine.value());
                String diffStr = (diff >= 0 ? "(+" : "(") + diff + ")";
                Formatting color = diff > 0 ? Formatting.GREEN : diff < 0 ? Formatting.RED : Formatting.GRAY;
                MutableText combined = hovLine.originalText().copy()
                        .append(Text.literal(" "))
                        .append(Text.literal(diffStr).formatted(color));
                result.add(combined);
            } else {
                result.add(hovLine.originalText());
            }
        }

        // Equipped-only attack speed (item has attack speed but hovered doesn't)
        appendEquippedOnly(equippedMap, result);

        return result;
    }

    private static List<Text> buildDamageGroup(List<StatLine> hoveredGroup, Map<String, StatLine> equippedMap) {
        List<Text> result = new ArrayList<>();

        // Split hovered into attack lines and DPS/average lines
        List<StatLine> hoveredAttacks = new ArrayList<>();
        List<StatLine> hoveredDps = new ArrayList<>();
        for (StatLine sl : hoveredGroup) {
            if (isDpsLine(sl)) {
                hoveredDps.add(sl);
            } else {
                hoveredAttacks.add(sl);
            }
        }

        // 1. Active attacks (hovered attack lines with diffs)
        processHoveredLines(hoveredAttacks, equippedMap, result);

        // 2. Average DPS lines
        processHoveredLines(hoveredDps, equippedMap, result);

        // 3. Inactive attacks (equipped-only as dark gray)
        appendEquippedOnly(equippedMap, result);

        return result;
    }

    /**
     * Builds a unique key for stat matching. Stats with the same label but different
     * value types (flat vs percent) are considered different stats.
     */
    private static String statKey(StatLine sl) {
        return sl.label() + (sl.isPercent() ? "%" : "");
    }

    private static boolean isDpsLine(StatLine sl) {
        String label = sl.label();
        return label.contains("DPS") || label.contains("Average");
    }

    private static void processHoveredLines(List<StatLine> hoveredLines, Map<String, StatLine> equippedMap, List<Text> result) {
        for (StatLine hovLine : hoveredLines) {
            StatLine eqLine = equippedMap.remove(statKey(hovLine));

            if (hovLine.isTextOnly()) {
                result.add(hovLine.originalText());
            } else if (eqLine != null && !eqLine.isTextOnly()) {
                if (hovLine.isRange() && eqLine.isRange()) {
                    Text diffText = formatRangeDiff(
                            hovLine.value() - eqLine.value(),
                            hovLine.valueMax() - eqLine.valueMax());
                    MutableText combined = hovLine.originalText().copy().append(Text.literal(" ")).append(diffText);
                    result.add(combined);
                } else {
                    double diff = hovLine.value() - eqLine.value();
                    Text diffText = formatDiff(diff, hovLine.isPercent());
                    MutableText combined = hovLine.originalText().copy().append(Text.literal(" ")).append(diffText);
                    result.add(combined);
                }
            } else {
                result.add(hovLine.originalText());
            }
        }
    }

    private static void appendEquippedOnly(Map<String, StatLine> equippedMap, List<Text> result) {
        for (StatLine eqOnly : equippedMap.values()) {
            result.add(toDarkGray(eqOnly.originalText()));
        }
    }

    private static MutableText toDarkGray(Text text) {
        // Preserve font (for icons) but override color and strip formatting
        Style darkStyle = text.getStyle()
                .withColor(Formatting.DARK_GRAY)
                .withBold(false)
                .withItalic(false)
                .withUnderline(false)
                .withStrikethrough(false)
                .withObfuscated(false);
        MutableText result = text.copyContentOnly().setStyle(darkStyle);
        for (Text sibling : text.getSiblings()) {
            result.append(toDarkGray(sibling));
        }
        return result;
    }

    private static Text formatRangeDiff(double diffMin, double diffMax) {
        String minStr = (diffMin >= 0 ? "+" : "") + formatNumber(diffMin);
        String maxStr = (diffMax >= 0 ? "+" : "") + formatNumber(diffMax);
        String text = "(" + minStr + ", " + maxStr + ")";

        // Color based on overall: green if both >= 0 and at least one > 0, red if both <= 0 and at least one < 0
        Formatting color;
        if (diffMin >= 0 && diffMax >= 0 && (diffMin > 0 || diffMax > 0)) {
            color = Formatting.GREEN;
        } else if (diffMin <= 0 && diffMax <= 0 && (diffMin < 0 || diffMax < 0)) {
            color = Formatting.RED;
        } else if (diffMin == 0 && diffMax == 0) {
            color = Formatting.GRAY;
        } else {
            color = Formatting.YELLOW;
        }
        return Text.literal(text).formatted(color);
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

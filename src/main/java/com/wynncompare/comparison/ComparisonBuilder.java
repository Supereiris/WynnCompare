package com.wynncompare.comparison;

import com.wynncompare.comparison.LoreParser.StatGroup;
import com.wynncompare.comparison.LoreParser.StatLine;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ComparisonBuilder {

    /**
     * Builds a styled comparison tooltip that mirrors the original tooltip layout.
     * Multi-value lines (defences, damage) are split into individual element lines.
     * Equipped-only elements are inserted grayed out after each section.
     */
    public static List<Text> buildStyled(List<Text> originalTooltipLines,
                                          List<StatLine> hoveredStats,
                                          List<StatLine> equippedStats) {
        // Build equipped stat map by key
        Map<String, StatLine> equippedMap = new LinkedHashMap<>();
        for (StatLine sl : equippedStats) {
            equippedMap.put(statKey(sl), sl);
        }

        // Build identity map: original Text line -> its parsed hovered StatLines
        Map<Text, List<StatLine>> hoveredLineMap = new IdentityHashMap<>();
        for (StatLine sl : hoveredStats) {
            hoveredLineMap.computeIfAbsent(sl.originalText(), k -> new ArrayList<>()).add(sl);
        }

        // Track which equipped stats have been matched
        Map<String, StatLine> unmatchedEquipped = new LinkedHashMap<>(equippedMap);

        List<Text> result = new ArrayList<>();
        int lastEffectIndex = -1;
        StatGroup previousGroup = null;

        for (Text line : originalTooltipLines) {
            List<StatLine> lineStats = hoveredLineMap.get(line);
            StatGroup currentGroup = (lineStats != null && !lineStats.isEmpty())
                    ? lineStats.get(0).group() : null;

            // When hitting a non-parsed line (divider, decoration) after a damage/defence section,
            // insert equipped-only elements. We use currentGroup == null (not just != previousGroup)
            // because DPS and elemental damage are both AVERAGE_DAMAGE but separated by Attack Speed.
            if (previousGroup != null && currentGroup == null) {
                if (previousGroup == StatGroup.AVERAGE_DAMAGE || previousGroup == StatGroup.DEFENCE) {
                    addEquippedOnlyForGroup(result, previousGroup, equippedStats, unmatchedEquipped);
                }
            }

            if (lineStats == null || lineStats.isEmpty()) {
                result.add(line);
                previousGroup = currentGroup;
                continue;
            }

            if (lineStats.size() == 1) {
                // Single stat: append diff at end of line
                StatLine hovLine = lineStats.get(0);
                String key = statKey(hovLine);
                StatLine eqLine = unmatchedEquipped.remove(key);

                if (eqLine != null && !eqLine.isTextOnly() && !hovLine.isTextOnly()) {
                    Text diff = computeDiff(hovLine, eqLine);
                    result.add(line.copy().append(Text.literal(" ")).append(diff));
                } else {
                    result.add(line);
                }
            } else {
                // Multiple stats on one line (defences, damage):
                // Split into individual element lines
                List<MutableText> fragments = splitElementLine(line);

                for (int i = 0; i < lineStats.size(); i++) {
                    StatLine hovLine = lineStats.get(i);
                    String key = statKey(hovLine);
                    StatLine eqLine = unmatchedEquipped.remove(key);

                    MutableText elemLine = i < fragments.size()
                            ? fragments.get(i).copy()
                            : Text.literal(hovLine.label()).formatted(Formatting.GRAY);

                    if (eqLine != null && !eqLine.isTextOnly() && !hovLine.isTextOnly()) {
                        elemLine.append(Text.literal(" ")).append(computeDiff(hovLine, eqLine));
                    }
                    result.add(elemLine);
                }
            }

            // Track last effect position for grayed-out insertion
            if (currentGroup == StatGroup.STAT) {
                lastEffectIndex = result.size();
            }

            previousGroup = currentGroup;
        }

        // Handle equipped-only for the last section if it was damage/defence
        if (previousGroup == StatGroup.AVERAGE_DAMAGE || previousGroup == StatGroup.DEFENCE) {
            addEquippedOnlyForGroup(result, previousGroup, equippedStats, unmatchedEquipped);
        }

        // Collect grayed-out equipped-only effects (STAT group only)
        List<Text> grayedOut = new ArrayList<>();
        for (StatLine eqOnly : unmatchedEquipped.values()) {
            if (eqOnly.isTextOnly()) continue;
            if (eqOnly.group() != StatGroup.STAT) continue;
            grayedOut.add(toDarkGray(eqOnly.originalText()));
        }

        // Insert after last effect line (before footer), or at end if no effects
        if (!grayedOut.isEmpty()) {
            int insertAt = lastEffectIndex >= 0 ? lastEffectIndex : result.size();
            result.addAll(insertAt, grayedOut);
        }

        return result;
    }

    /**
     * Splits a multi-element line (defences/damage) at each attribute sprite icon,
     * producing one fragment per element with its icon + spacing + value.
     * Flattens the tree to leaf nodes first so nested structures are handled.
     */
    private static List<MutableText> splitElementLine(Text line) {
        // Flatten: collect all leaf Text nodes (nodes with actual content) in tree order
        List<Text> leaves = new ArrayList<>();
        collectLeaves(line, leaves);

        // Split at attribute sprite leaves
        List<MutableText> elements = new ArrayList<>();
        MutableText current = null;

        for (Text leaf : leaves) {
            Identifier fontId = LoreParser.getFontId(leaf.getStyle().getFont());
            if (fontId.equals(LoreParser.ATTRIBUTE_SPRITE_FONT)) {
                if (current != null) {
                    elements.add(current);
                }
                current = Text.empty().copy();
            }
            if (current == null) {
                current = Text.empty().copy();
            }
            current.append(leaf.copyContentOnly().setStyle(leaf.getStyle()));
        }
        if (current != null && !elements.isEmpty()) {
            elements.add(current);
        } else if (current != null && elements.isEmpty() && leaves.stream().anyMatch(
                l -> LoreParser.getFontId(l.getStyle().getFont()).equals(LoreParser.ATTRIBUTE_SPRITE_FONT))) {
            elements.add(current);
        }

        return elements;
    }

    /**
     * Recursively collects all leaf Text nodes (those with non-empty content) in tree order.
     */
    private static void collectLeaves(Text text, List<Text> out) {
        String content = text.copyContentOnly().getString();
        if (!content.isEmpty()) {
            out.add(text);
        }
        for (Text child : text.getSiblings()) {
            collectLeaves(child, out);
        }
    }

    /**
     * Inserts equipped-only elements for a damage/defence group, grayed out.
     * For groups where all stats share one line (defence): splits the line into fragments.
     * For groups where each stat has its own line (damage): uses the original line directly.
     */
    private static void addEquippedOnlyForGroup(List<Text> result, StatGroup group,
                                                 List<StatLine> equippedStats,
                                                 Map<String, StatLine> unmatchedEquipped) {
        // Collect unmatched equipped stats of this group in parse order
        List<StatLine> eqGroupStats = new ArrayList<>();
        for (StatLine eq : equippedStats) {
            if (eq.group() == group && unmatchedEquipped.containsKey(statKey(eq))) {
                eqGroupStats.add(eq);
            }
        }
        if (eqGroupStats.isEmpty()) return;

        // Build full list of ALL equipped stats per originalText (including matched ones)
        // so we can determine each stat's position index within its line
        IdentityHashMap<Text, List<StatLine>> allStatsByLine = new IdentityHashMap<>();
        for (StatLine eq : equippedStats) {
            if (eq.group() == group) {
                allStatsByLine.computeIfAbsent(eq.originalText(), k -> new ArrayList<>()).add(eq);
            }
        }

        // Group unmatched stats by originalText identity, preserving encounter order
        IdentityHashMap<Text, List<StatLine>> byLine = new IdentityHashMap<>();
        List<Text> lineOrder = new ArrayList<>();
        for (StatLine eq : eqGroupStats) {
            if (!byLine.containsKey(eq.originalText())) {
                lineOrder.add(eq.originalText());
            }
            byLine.computeIfAbsent(eq.originalText(), k -> new ArrayList<>()).add(eq);
        }

        for (Text lineText : lineOrder) {
            List<StatLine> unmatchedOnLine = byLine.get(lineText);
            List<StatLine> allOnLine = allStatsByLine.getOrDefault(lineText, List.of());

            if (allOnLine.size() > 1) {
                // This is a shared multi-element line — split into fragments
                List<MutableText> fragments = splitElementLine(lineText);
                // For each unmatched stat, find its position in the full stat list for this line
                for (StatLine unmatched : unmatchedOnLine) {
                    int posInLine = allOnLine.indexOf(unmatched);
                    unmatchedEquipped.remove(statKey(unmatched));
                    if (posInLine >= 0 && posInLine < fragments.size()) {
                        result.add(toDarkGray(fragments.get(posInLine)));
                    } else {
                        // Fallback: show the whole line
                        result.add(toDarkGray(lineText));
                    }
                }
            } else {
                // Single stat on this line — show directly
                unmatchedEquipped.remove(statKey(unmatchedOnLine.get(0)));
                result.add(toDarkGray(lineText));
            }
        }
    }

    private static Text computeDiff(StatLine hovLine, StatLine eqLine) {
        if (hovLine.group() == StatGroup.ATTACK_SPEED) {
            int diff = (int) (hovLine.value() - eqLine.value());
            String diffStr = (diff >= 0 ? "(+" : "(") + diff + ")";
            Formatting color = diff > 0 ? Formatting.GREEN : diff < 0 ? Formatting.RED : Formatting.GRAY;
            return Text.literal(diffStr).formatted(color);
        }

        if (hovLine.isRange() && eqLine.isRange()) {
            return formatRangeDiff(
                    hovLine.value() - eqLine.value(),
                    hovLine.valueMax() - eqLine.valueMax());
        }

        double diff = hovLine.value() - eqLine.value();
        return formatDiff(diff, hovLine.isPercent());
    }

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

        appendEquippedOnly(equippedMap, result);

        return result;
    }

    private static List<Text> buildDamageGroup(List<StatLine> hoveredGroup, Map<String, StatLine> equippedMap) {
        List<Text> result = new ArrayList<>();

        List<StatLine> hoveredAttacks = new ArrayList<>();
        List<StatLine> hoveredDps = new ArrayList<>();
        for (StatLine sl : hoveredGroup) {
            if (isDpsLine(sl)) {
                hoveredDps.add(sl);
            } else {
                hoveredAttacks.add(sl);
            }
        }

        processHoveredLines(hoveredAttacks, equippedMap, result);
        processHoveredLines(hoveredDps, equippedMap, result);
        appendEquippedOnly(equippedMap, result);

        return result;
    }

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

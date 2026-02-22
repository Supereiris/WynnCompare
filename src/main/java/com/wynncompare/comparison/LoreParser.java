package com.wynncompare.comparison;

import com.wynncompare.item.WynnItemParser;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoreParser {

    public enum StatGroup {
        HEALTH, DEFENCE, AVERAGE_DAMAGE, REQUIREMENT, ATTRIBUTE, STAT, NONE
    }

    private enum PatternType {
        SUFFIX, PREFIX_PCT, PREFIX_FLAT, TEXT_ONLY
    }

    public record StatLine(String label, double value, double valueMax, boolean isPercent,
                           boolean isRange, boolean isStat, boolean isTextOnly,
                           StatGroup group, Text originalText) {}

    // Pattern A: "Label: +/-N" or "Label: N" (e.g., "Health: +251", "Water Defence: 80")
    private static final Pattern PATTERN_SUFFIX = Pattern.compile("^(.+?):\\s*([+-]?\\d+)\\s*$");

    // Pattern A2: "Label: N-N" range (e.g., "Neutral Damage: 4-11")
    private static final Pattern PATTERN_SUFFIX_RANGE = Pattern.compile("^(.+?):\\s*(\\d+)-(\\d+)\\s*$");

    // Pattern B: "+/-N% Label" or "+/-N%** Label" (e.g., "+24%** XP Bonus", "+12% Reflection")
    private static final Pattern PATTERN_PREFIX_PCT = Pattern.compile("^([+-]?\\d+)%(\\**)\\s+(.+)$");

    // Pattern C: "+/-N Label" or "+/-N* Label" without percent (e.g., "+5 Strength", "+5* Cost")
    private static final Pattern PATTERN_PREFIX_FLAT = Pattern.compile("^([+-]\\d+)\\*?\\s+(.+)$");

    // Pattern E: "+/-N/Ns Label" ratio stats (e.g., "+4/5s Mana Regen")
    private static final Pattern PATTERN_PREFIX_RATIO = Pattern.compile("^([+-]?\\d+/\\d+s?)\\s+(.+)$");

    // Pattern D: Non-numeric text-only lines like "Class Req: Assassin" or "+AbilityName: description"
    private static final Pattern PATTERN_TEXT_ONLY = Pattern.compile("^(.+?):\\s*(.+)$");

    // Leading unicode symbols to strip from labels
    private static final Pattern LEADING_SYMBOLS = Pattern.compile("^[^\\w\\s]+\\s*");

    private static final Set<String> SKIP_KEYWORDS = Set.of(
            "Attack Speed", "Powder Slots", "Quest Req"
    );

    private static final Set<String> ATTRIBUTE_NAMES = Set.of(
            "Strength", "Dexterity", "Intelligence", "Agility", "Defence"
    );

    public static List<StatLine> parse(List<Text> lines) {
        List<StatLine> result = new ArrayList<>();

        for (Text line : lines) {
            String raw = stripSectionCodes(line.getString()).trim();

            if (raw.isEmpty()) {
                continue;
            }

            StatLine parsed = parseLine(raw, line);
            if (parsed.group() != StatGroup.NONE) {
                result.add(parsed);
            }
        }

        return result;
    }

    private static StatLine parseLine(String raw, Text originalText) {
        // Check if it's a rarity marker line
        for (String marker : WynnItemParser.RARITY_MARKERS) {
            if (raw.contains(marker)) {
                return nonStat(raw, originalText);
            }
        }

        // Check skip keywords — these are always NONE
        for (String keyword : SKIP_KEYWORDS) {
            if (raw.contains(keyword)) {
                return nonStat(raw, originalText);
            }
        }

        // Try Pattern E: "+/-N/Ns Label" ratio stats (e.g., "+4/5s Mana Regen")
        Matcher mRatio = PATTERN_PREFIX_RATIO.matcher(raw);
        if (mRatio.matches()) {
            String label = normalizeLabel(mRatio.group(2));
            return new StatLine(label, 0, 0, false, false, true, true, StatGroup.STAT, originalText);
        }

        // Try Pattern B: "+/-N% Label" or "+/-N%** Label"
        Matcher mPct = PATTERN_PREFIX_PCT.matcher(raw);
        if (mPct.matches()) {
            double value = Double.parseDouble(mPct.group(1));
            String label = normalizeLabel(mPct.group(3));
            StatGroup group = classifyGroup(label, raw, PatternType.PREFIX_PCT);
            return new StatLine(label, value, 0, true, false, true, false, group, originalText);
        }

        // Try Pattern C: "+/-N Label" (flat prefix)
        Matcher mFlat = PATTERN_PREFIX_FLAT.matcher(raw);
        if (mFlat.matches()) {
            double value = Double.parseDouble(mFlat.group(1));
            String label = normalizeLabel(mFlat.group(2));
            StatGroup group = classifyGroup(label, raw, PatternType.PREFIX_FLAT);
            return new StatLine(label, value, 0, false, false, true, false, group, originalText);
        }

        // Try Pattern A2: "Label: N-N" range (before single-value suffix)
        Matcher mRange = PATTERN_SUFFIX_RANGE.matcher(raw);
        if (mRange.matches()) {
            String label = normalizeLabel(mRange.group(1));
            double min = Double.parseDouble(mRange.group(2));
            double max = Double.parseDouble(mRange.group(3));
            StatGroup group = classifyGroup(label, raw, PatternType.SUFFIX);
            return new StatLine(label, min, max, false, true, true, false, group, originalText);
        }

        // Try Pattern A: "Label: +/-N"
        Matcher mSuffix = PATTERN_SUFFIX.matcher(raw);
        if (mSuffix.matches()) {
            String label = normalizeLabel(mSuffix.group(1));
            double value = Double.parseDouble(mSuffix.group(2));
            StatGroup group = classifyGroup(label, raw, PatternType.SUFFIX);
            return new StatLine(label, value, 0, false, false, true, false, group, originalText);
        }

        // Try Pattern D: text-only lines like "Class Req: Assassin"
        Matcher mText = PATTERN_TEXT_ONLY.matcher(raw);
        if (mText.matches()) {
            String label = normalizeLabel(mText.group(1));
            StatGroup group = classifyGroup(label, raw, PatternType.TEXT_ONLY);
            if (group != StatGroup.NONE) {
                return new StatLine(label, 0, 0, false, false, true, true, group, originalText);
            }
        }

        // Non-stat line
        return nonStat(raw, originalText);
    }

    private static StatLine nonStat(String raw, Text originalText) {
        return new StatLine(raw, 0, 0, false, false, false, false, StatGroup.NONE, originalText);
    }

    private static StatGroup classifyGroup(String label, String raw, PatternType pattern) {
        switch (pattern) {
            case SUFFIX:
                // "Label: N" format — Requirements first (must check before Defence)
                if (raw.contains("Lv. Min") || label.endsWith("Min")) {
                    return StatGroup.REQUIREMENT;
                }
                if (raw.contains("Health")) {
                    return StatGroup.HEALTH;
                }
                if (raw.contains("Defence")) {
                    return StatGroup.DEFENCE;
                }
                if (raw.contains("Damage") || raw.contains("Attack")
                        || raw.contains("DPS") || raw.contains("Average")) {
                    return StatGroup.AVERAGE_DAMAGE;
                }
                return StatGroup.STAT;

            case PREFIX_PCT:
                // "+N% Label" format — always a stat or attribute
                if (ATTRIBUTE_NAMES.contains(label)) {
                    return StatGroup.ATTRIBUTE;
                }
                return StatGroup.STAT;

            case PREFIX_FLAT:
                // "+N Label" format — attribute or stat
                if (ATTRIBUTE_NAMES.contains(label)) {
                    return StatGroup.ATTRIBUTE;
                }
                return StatGroup.STAT;

            case TEXT_ONLY:
                // "Label: text" — Class Req or ability text
                if (raw.contains("Class Req")) {
                    return StatGroup.REQUIREMENT;
                }
                // Only include as STAT if it looks like an ability/stat (not random text)
                if (raw.startsWith("+")) {
                    return StatGroup.STAT;
                }
                return StatGroup.NONE;

            default:
                return StatGroup.NONE;
        }
    }

    private static String normalizeLabel(String label) {
        return LEADING_SYMBOLS.matcher(label).replaceFirst("").trim();
    }

    private static String stripSectionCodes(String text) {
        return text.replaceAll("§.", "");
    }
}

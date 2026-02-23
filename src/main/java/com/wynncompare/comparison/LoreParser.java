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
        HEALTH, DEFENCE, ATTACK_SPEED, AVERAGE_DAMAGE, REQUIREMENT, ATTRIBUTE, STAT, NONE
    }

    private enum PatternType {
        SUFFIX, PREFIX_PCT, PREFIX_FLAT, TEXT_ONLY
    }

    public record StatLine(String label, double value, double valueMax, boolean isPercent,
                           boolean isRange, boolean isStat, boolean isTextOnly,
                           StatGroup group, Text originalText) {}

    // Pattern A: "Label: +/-N" or "Label: N", optional stars (e.g., "Health: +251", "Water Defence: 80*")
    private static final Pattern PATTERN_SUFFIX = Pattern.compile("^(.+?):\\s*([+-]?\\d+)\\*{0,3}\\s*$");

    // Pattern A2: "Label: N-N" range, optional stars (e.g., "Neutral Damage: 4-11", "Fire Damage: 8-15**")
    private static final Pattern PATTERN_SUFFIX_RANGE = Pattern.compile("^(.+?):\\s*(\\d+)-(\\d+)\\*{0,3}\\s*$");

    // Pattern B: "+/-N% Label", optional stars (e.g., "+24%** XP Bonus", "+12% Reflection")
    private static final Pattern PATTERN_PREFIX_PCT = Pattern.compile("^([+-]?\\d+)%\\*{0,3}\\s+(.+)$");

    // Pattern C: "+/-N Label", optional stars (e.g., "+5 Strength", "+5*** Cost")
    private static final Pattern PATTERN_PREFIX_FLAT = Pattern.compile("^([+-]\\d+)\\*{0,3}\\s+(.+)$");

    // Pattern E: "+/-N/Ns Label" ratio stats, optional stars (e.g., "+4/5s Mana Regen", "+4/5s** Mana Regen")
    // Group 1 = signed numerator, group 2 = label
    private static final Pattern PATTERN_PREFIX_RATIO = Pattern.compile("^([+-]?\\d+)/\\d+s?\\*{0,3}\\s+(.+)$");

    // Pattern F: "+/-N tier Label", optional stars (e.g., "+1 tier Attack Speed", "-2 tier** Attack Speed")
    private static final Pattern PATTERN_PREFIX_TIER = Pattern.compile("^([+-]?\\d+)\\s+tier\\*{0,3}\\s+(.+)$");

    // Pattern D: Non-numeric text-only lines like "Class Req: Assassin" or "+AbilityName: description"
    private static final Pattern PATTERN_TEXT_ONLY = Pattern.compile("^(.+?):\\s*(.+)$");

    // Leading unicode symbols to strip from labels
    private static final Pattern LEADING_SYMBOLS = Pattern.compile("^[^\\w\\s]+\\s*");

    private static final Set<String> SKIP_KEYWORDS = Set.of(
            "Powder Slots", "Quest Req"
    );

    // Attack speed tiers in order from slowest to fastest
    public static final List<String> ATTACK_SPEED_TIERS = List.of(
            "Super Slow", "Very Slow", "Slow", "Normal", "Fast", "Very Fast", "Super Fast"
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

        // Check for attack speed line (e.g., "Super Fast Attack Speed")
        if (raw.contains("Attack Speed")) {
            // Try absolute tier name (e.g., "Super Fast Attack Speed")
            for (String tier : ATTACK_SPEED_TIERS) {
                if (raw.contains(tier)) {
                    int tierIndex = ATTACK_SPEED_TIERS.indexOf(tier);
                    return new StatLine("Attack Speed", tierIndex, 0, false, false, true, false,
                            StatGroup.ATTACK_SPEED, originalText);
                }
            }
            // Fall through to general patterns (e.g., "+1 tier Attack Speed")
        }

        // Try Pattern F: "+/-N tier Label" (e.g., "+1 tier Attack Speed")
        Matcher mTier = PATTERN_PREFIX_TIER.matcher(raw);
        if (mTier.matches()) {
            double value = Double.parseDouble(mTier.group(1));
            String label = normalizeLabel(mTier.group(2));
            return new StatLine(label, value, 0, false, false, true, false, StatGroup.STAT, originalText);
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
            double value = Double.parseDouble(mRatio.group(1));
            String label = normalizeLabel(mRatio.group(2));
            StatGroup group = classifyGroup(label, raw, PatternType.PREFIX_FLAT);
            return new StatLine(label, value, 0, false, false, true, false, group, originalText);
        }

        // Try Pattern B: "+/-N% Label" or "+/-N%** Label"
        Matcher mPct = PATTERN_PREFIX_PCT.matcher(raw);
        if (mPct.matches()) {
            double value = Double.parseDouble(mPct.group(1));
            String label = normalizeLabel(mPct.group(2));
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

        // Try Pattern D: text-only lines like "Class Req: Assassin" or "+Ability: description"
        Matcher mText = PATTERN_TEXT_ONLY.matcher(raw);
        if (mText.matches()) {
            String label = normalizeLabel(mText.group(1));
            StatGroup group = classifyGroup(label, raw, PatternType.TEXT_ONLY);
            if (group != StatGroup.NONE) {
                // For ability stats (+Name: description), store only "+Name" as display text
                Text displayText = originalText;
                if (raw.startsWith("+") && group == StatGroup.STAT) {
                    displayText = Text.literal("+" + label).setStyle(originalText.getStyle());
                }
                return new StatLine(label, 0, 0, false, false, true, true, group, displayText);
            }
        }

        // Ability lines: "+AbilityName" or "+AbilityName:" (description may be on next line or absent)
        if (raw.startsWith("+")) {
            String stripped = raw.startsWith("+") ? raw.substring(1) : raw;
            // Remove trailing colon if present
            if (stripped.endsWith(":")) {
                stripped = stripped.substring(0, stripped.length() - 1);
            }
            String label = normalizeLabel(stripped.trim());
            if (!label.isEmpty()) {
                Text displayText = Text.literal("+" + label).setStyle(originalText.getStyle());
                return new StatLine(label, 0, 0, false, false, true, true, StatGroup.STAT, displayText);
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

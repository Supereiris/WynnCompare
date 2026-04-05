package com.wynncompare.comparison;

import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoreParser {

    public enum StatGroup {
        HEALTH, DEFENCE, ATTACK_SPEED, AVERAGE_DAMAGE, REQUIREMENT, ATTRIBUTE, STAT, NONE
    }

    public record StatLine(String label, double value, double valueMax, boolean isPercent,
                           boolean isRange, boolean isStat, boolean isTextOnly,
                           StatGroup group, Text originalText) {}

    private record Segment(String content, TextColor color, Identifier font) {}

    // Fonts that indicate a purely decorative/structural line (skip entire line)
    private static final Set<Identifier> LINE_SKIP_FONTS = Set.of(
            Identifier.of("minecraft", "tooltip/divider"),
            Identifier.of("minecraft", "tooltip/banner"),
            Identifier.of("minecraft", "banner/box"),
            Identifier.of("minecraft", "tooltip/emblem/frame"),
            Identifier.of("minecraft", "tooltip/emblem/sprite"),
            Identifier.of("minecraft", "tooltip/page"),
            Identifier.of("minecraft", "chat/tile")
    );

    // Fonts whose segments carry icons/spacing, not readable text
    private static final Set<Identifier> DECORATIVE_SEGMENT_FONTS = Set.of(
            Identifier.of("minecraft", "tooltip/divider"),
            Identifier.of("minecraft", "tooltip/banner"),
            Identifier.of("minecraft", "banner/box"),
            Identifier.of("minecraft", "tooltip/emblem/frame"),
            Identifier.of("minecraft", "tooltip/emblem/sprite"),
            Identifier.of("minecraft", "tooltip/page"),
            Identifier.of("minecraft", "chat/tile"),
            Identifier.of("minecraft", "space"),
            Identifier.of("minecraft", "tooltip/identification/meter"),
            Identifier.of("minecraft", "tooltip/requirement/sprite"),
            Identifier.of("minecraft", "tooltip/attribute/sprite")
    );

    private static final Identifier REQUIREMENT_FONT = Identifier.of("minecraft", "tooltip/requirement/sprite");

    public static final List<String> ATTACK_SPEED_TIERS = List.of(
            "Super Slow", "Very Slow", "Slow", "Normal", "Fast", "Very Fast", "Super Fast"
    );

    private static final Set<String> ATTRIBUTE_NAMES = Set.of(
            "Strength", "Dexterity", "Intelligence", "Agility", "Defence"
    );

    // Signed stat value: +N, -N, +N%, -N%, +N/Ns, -N/Ns
    private static final Pattern SIGNED_VALUE = Pattern.compile("^([+-]\\d+)(/\\d+s?|%)?$");

    // Damage range: N-N
    private static final Pattern RANGE_VALUE = Pattern.compile("^(\\d+)-(\\d+)$");

    // DPS extraction from a single segment like "56 DPS"
    private static final Pattern DPS_IN_SEGMENT = Pattern.compile("(\\d+)\\s*DPS");

    public static List<StatLine> parse(List<Text> lines) {
        List<StatLine> result = new ArrayList<>();
        for (Text line : lines) {
            result.addAll(parseLine(line));
        }
        return result;
    }

    private static List<StatLine> parseLine(Text line) {
        List<Segment> all = new ArrayList<>();
        collectSegments(line, all);

        // Skip lines containing decorative/structural fonts
        for (Segment seg : all) {
            if (LINE_SKIP_FONTS.contains(seg.font())) {
                return List.of();
            }
        }

        // Check if line has requirement icon
        boolean isRequirement = false;
        for (Segment seg : all) {
            if (seg.font().equals(REQUIREMENT_FONT)) {
                isRequirement = true;
                break;
            }
        }

        // Filter to meaningful content segments (strip decorative fonts and PUA chars)
        List<Segment> content = new ArrayList<>();
        for (Segment seg : all) {
            if (DECORATIVE_SEGMENT_FONTS.contains(seg.font())) continue;
            String stripped = stripPUA(seg.content());
            if (!stripped.isBlank()) {
                content.add(new Segment(stripped.trim(), seg.color(), seg.font()));
            }
        }

        if (content.isEmpty()) {
            return List.of();
        }

        // Try parsers in priority order
        List<StatLine> result;

        if ((result = tryParseDPS(content, line)) != null) return result;
        if ((result = tryParseAttackSpeed(content, line)) != null) return result;
        if ((result = tryParseDamageRange(content, line)) != null) return result;
        if (isRequirement && (result = tryParseRequirement(content, line)) != null) return result;
        if ((result = tryParseStatValue(content, line)) != null) return result;

        return List.of();
    }

    private static void collectSegments(Text text, List<Segment> out) {
        String content = text.copyContentOnly().getString();
        if (!content.isEmpty()) {
            Identifier fontId = getFontId(text.getStyle().getFont());
            out.add(new Segment(content, text.getStyle().getColor(), fontId));
        }
        for (Text sibling : text.getSiblings()) {
            collectSegments(sibling, out);
        }
    }

    private static Identifier getFontId(StyleSpriteSource font) {
        if (font instanceof StyleSpriteSource.Font f) {
            return f.id();
        }
        return Identifier.of("minecraft", "default");
    }

    // DPS line: segments containing "DPS" with a preceding number
    private static List<StatLine> tryParseDPS(List<Segment> content, Text line) {
        for (int i = 0; i < content.size(); i++) {
            String text = content.get(i).content();

            // Check if DPS and number are in the same segment
            Matcher m = DPS_IN_SEGMENT.matcher(text);
            if (m.find()) {
                double value = Double.parseDouble(m.group(1));
                return List.of(new StatLine("Average DPS", value, 0, false, false, true, false,
                        StatGroup.AVERAGE_DAMAGE, line));
            }

            // Check if "DPS" is in this segment and number is in the previous one
            if (text.contains("DPS") && i > 0) {
                String prev = content.get(i - 1).content().trim();
                try {
                    double value = Double.parseDouble(prev);
                    return List.of(new StatLine("Average DPS", value, 0, false, false, true, false,
                            StatGroup.AVERAGE_DAMAGE, line));
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    // Attack speed: line containing "hits/s" with a tier name
    private static List<StatLine> tryParseAttackSpeed(List<Segment> content, Text line) {
        StringBuilder sb = new StringBuilder();
        for (Segment seg : content) {
            sb.append(seg.content()).append(" ");
        }
        String joined = sb.toString();

        if (!joined.contains("hits/s")) {
            return null;
        }

        // Check longest tier names first to match "Super Fast" before "Fast"
        for (int i = ATTACK_SPEED_TIERS.size() - 1; i >= 0; i--) {
            if (joined.contains(ATTACK_SPEED_TIERS.get(i))) {
                return List.of(new StatLine("Attack Speed", i, 0, false, false, true, false,
                        StatGroup.ATTACK_SPEED, line));
            }
        }

        return null;
    }

    // Damage ranges: segments matching N-N pattern
    private static List<StatLine> tryParseDamageRange(List<Segment> content, Text line) {
        List<StatLine> ranges = new ArrayList<>();
        int index = 1;

        for (Segment seg : content) {
            Matcher m = RANGE_VALUE.matcher(seg.content());
            if (m.matches()) {
                double min = Double.parseDouble(m.group(1));
                double max = Double.parseDouble(m.group(2));
                String label = "Damage " + index;
                Text syntheticText = Text.literal(m.group(1) + "-" + m.group(2)).formatted(Formatting.GRAY);
                ranges.add(new StatLine(label, min, max, false, true, true, false,
                        StatGroup.AVERAGE_DAMAGE, syntheticText));
                index++;
            }
        }

        return ranges.isEmpty() ? null : ranges;
    }

    // Requirement lines: icon present, label + value structure
    private static List<StatLine> tryParseRequirement(List<Segment> content, Text line) {
        if (content.size() < 2) return null;

        Segment valueSeg = content.get(content.size() - 1);
        StringBuilder labelBuilder = new StringBuilder();
        for (int i = 0; i < content.size() - 1; i++) {
            if (!labelBuilder.isEmpty()) labelBuilder.append(" ");
            labelBuilder.append(content.get(i).content());
        }
        String label = labelBuilder.toString().trim();

        try {
            double value = Double.parseDouble(valueSeg.content());
            return List.of(new StatLine(label, value, 0, false, false, true, false,
                    StatGroup.REQUIREMENT, line));
        } catch (NumberFormatException e) {
            // Text requirement (e.g., Class Type → Assassin/Ninja)
            return List.of(new StatLine(label, 0, 0, false, false, true, true,
                    StatGroup.REQUIREMENT, line));
        }
    }

    // Stat/attribute values: signed value pattern (+N, +N%, +N/Ns)
    private static List<StatLine> tryParseStatValue(List<Segment> content, Text line) {
        for (int i = 0; i < content.size(); i++) {
            Matcher m = SIGNED_VALUE.matcher(content.get(i).content());
            if (m.matches()) {
                double value = Double.parseDouble(m.group(1));
                String suffix = m.group(2);
                boolean isPercent = "%".equals(suffix);

                // Collect label from preceding segments
                StringBuilder labelBuilder = new StringBuilder();
                for (int j = 0; j < i; j++) {
                    String segText = content.get(j).content();
                    if (!segText.isBlank()) {
                        if (!labelBuilder.isEmpty()) labelBuilder.append(" ");
                        labelBuilder.append(segText);
                    }
                }
                String label = labelBuilder.toString().trim();
                if (label.isEmpty()) continue;

                StatGroup group = classifyByLabel(label);
                return List.of(new StatLine(label, value, 0, isPercent, false, true, false,
                        group, line));
            }
        }
        return null;
    }

    private static StatGroup classifyByLabel(String label) {
        if (ATTRIBUTE_NAMES.contains(label)) return StatGroup.ATTRIBUTE;
        if (label.equals("Health")) return StatGroup.HEALTH;
        if (label.endsWith("Defence")) return StatGroup.DEFENCE;
        if (label.contains("Damage") || label.contains("DPS") || label.contains("Average")) {
            return StatGroup.AVERAGE_DAMAGE;
        }
        return StatGroup.STAT;
    }

    private static String stripPUA(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (!isPUA(cp)) {
                sb.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static boolean isPUA(int codePoint) {
        return (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFF)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFF);
    }
}

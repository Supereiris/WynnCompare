package com.wynncompare.comparison;

import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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

    // Header/footer fonts — lines with these are skipped in styled comparison output
    private static final Set<Identifier> HEADER_FOOTER_FONTS = Set.of(
            Identifier.of("minecraft", "tooltip/banner"),
            Identifier.of("minecraft", "banner/box"),
            Identifier.of("minecraft", "tooltip/emblem/frame"),
            Identifier.of("minecraft", "tooltip/emblem/sprite"),
            Identifier.of("minecraft", "tooltip/page"),
            Identifier.of("minecraft", "chat/tile")
    );

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
    static final Set<Identifier> DECORATIVE_SEGMENT_FONTS = Set.of(
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
    static final Identifier ATTRIBUTE_SPRITE_FONT = Identifier.of("minecraft", "tooltip/attribute/sprite");

    public static final List<String> ATTACK_SPEED_TIERS = List.of(
            "Super Slow", "Very Slow", "Slow", "Normal", "Fast", "Very Fast", "Super Fast"
    );

    private static final Set<String> ATTRIBUTE_NAMES = Set.of(
            "Strength", "Dexterity", "Intelligence", "Agility", "Defence"
    );

    // Signed stat value: +N, -N, +N%, -N%, +N/Ns, -N/Ns (with optional commas in number)
    static final Pattern SIGNED_VALUE = Pattern.compile("^([+-][\\d,]+)(/\\d+s?|%)?$");

    // Damage range: N-N
    static final Pattern RANGE_VALUE = Pattern.compile("^(\\d+)-(\\d+)$");

    // Unsigned number (for zero defences etc.)
    private static final Pattern UNSIGNED_NUMBER = Pattern.compile("^\\d[\\d,]*$");

    // DPS extraction from a single segment like "56 DPS"
    private static final Pattern DPS_IN_SEGMENT = Pattern.compile("([\\d,]+)\\s*DPS");

    public static List<StatLine> parse(List<Text> lines) {
        List<StatLine> result = new ArrayList<>();
        for (Text line : lines) {
            result.addAll(parseLine(line));
        }
        return result;
    }

    /**
     * Parse stats and build a map from original Text line reference to its parsed StatLines.
     * Uses identity equality so the same Text object can be looked up later.
     */
    public static List<StatLine> parseWithLineMap(List<Text> lines, Map<Text, List<StatLine>> lineMap) {
        List<StatLine> result = new ArrayList<>();
        for (Text line : lines) {
            List<StatLine> parsed = parseLine(line);
            if (!parsed.isEmpty()) {
                lineMap.put(line, parsed);
            }
            result.addAll(parsed);
        }
        return result;
    }

    /**
     * Returns true if the line is a header/footer decorative line that should be
     * skipped in the styled comparison tooltip (banners, emblems, page markers).
     */
    public static boolean isSkippedLine(Text line) {
        return containsAnyFont(line, HEADER_FOOTER_FONTS);
    }

    /**
     * Returns true if the line contains a divider font segment.
     */
    public static boolean isDividerLine(Text line) {
        return containsAnyFont(line, Set.of(Identifier.of("minecraft", "tooltip/divider")));
    }

    private static boolean containsAnyFont(Text text, Set<Identifier> fonts) {
        Identifier fontId = getFontId(text.getStyle().getFont());
        if (fonts.contains(fontId)) return true;
        for (Text sibling : text.getSiblings()) {
            if (containsAnyFont(sibling, fonts)) return true;
        }
        return false;
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

        // Check line-level markers
        boolean isRequirement = false;
        boolean hasAttributeSprite = false;
        for (Segment seg : all) {
            if (seg.font().equals(REQUIREMENT_FONT)) isRequirement = true;
            if (seg.font().equals(ATTRIBUTE_SPRITE_FONT)) hasAttributeSprite = true;
        }

        // Filter to meaningful content segments (strip decorative fonts and non-printable chars)
        List<Segment> content = new ArrayList<>();
        for (Segment seg : all) {
            if (DECORATIVE_SEGMENT_FONTS.contains(seg.font())) continue;
            String stripped = stripNonPrintable(seg.content());
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
        if (hasAttributeSprite && (result = tryParseDamageRange(content, all, line)) != null) return result;
        if (hasAttributeSprite && (result = tryParseDefenceValues(content, all, line)) != null) return result;
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

    static Identifier getFontId(StyleSpriteSource font) {
        if (font instanceof StyleSpriteSource.Font f) {
            return f.id();
        }
        return Identifier.of("minecraft", "default");
    }

    // DPS line: segments containing "DPS" with a preceding number
    private static List<StatLine> tryParseDPS(List<Segment> content, Text line) {
        for (int i = 0; i < content.size(); i++) {
            String text = content.get(i).content();

            Matcher m = DPS_IN_SEGMENT.matcher(text);
            if (m.find()) {
                double value = parseNumber(m.group(1));
                return List.of(new StatLine("Average DPS", value, 0, false, false, true, false,
                        StatGroup.AVERAGE_DAMAGE, line));
            }

            if (text.contains("DPS") && i > 0) {
                String prev = content.get(i - 1).content().trim();
                try {
                    double value = parseNumber(prev);
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

        for (int i = ATTACK_SPEED_TIERS.size() - 1; i >= 0; i--) {
            if (joined.contains(ATTACK_SPEED_TIERS.get(i))) {
                return List.of(new StatLine("Attack Speed", i, 0, false, false, true, false,
                        StatGroup.ATTACK_SPEED, line));
            }
        }

        return null;
    }

    /**
     * Damage ranges: segments matching N-N pattern.
     * Uses the preceding attribute sprite icon's color to identify the element,
     * so matching between items with different elements works correctly.
     */
    private static List<StatLine> tryParseDamageRange(List<Segment> content, List<Segment> allSegments, Text line) {
        // Validate: at least one segment must be a range
        boolean hasRange = false;
        for (Segment seg : content) {
            if (RANGE_VALUE.matcher(seg.content()).matches()) {
                hasRange = true;
                break;
            }
        }
        if (!hasRange) return null;

        List<StatLine> ranges = new ArrayList<>();
        String lastIconKey = null;
        int fallbackIndex = 0;

        for (Segment seg : allSegments) {
            if (seg.font().equals(ATTRIBUTE_SPRITE_FONT)) {
                lastIconKey = encodeCodePoints(seg.content());
                continue;
            }

            String stripped = stripNonPrintable(seg.content()).trim();
            if (stripped.isEmpty() || DECORATIVE_SEGMENT_FONTS.contains(seg.font())) continue;

            Matcher m = RANGE_VALUE.matcher(stripped);
            if (m.matches()) {
                double min = Double.parseDouble(m.group(1));
                double max = Double.parseDouble(m.group(2));
                String key = lastIconKey != null ? lastIconKey : "pos" + fallbackIndex;
                String label = "ElemDmg " + key;
                ranges.add(new StatLine(label, min, max, false, true, true, false,
                        StatGroup.AVERAGE_DAMAGE, line));
                fallbackIndex++;
                lastIconKey = null;
            }
        }

        return ranges.isEmpty() ? null : ranges;
    }

    // Wynncraft element names in display order, mapped by the color of the preceding icon sprite
    private static final String[] ELEMENT_NAMES = {"Earth", "Thunder", "Water", "Fire", "Air"};

    /**
     * Elemental defence values: lines with attribute sprite icons and only signed numbers.
     * Labels each defence by the element color of its preceding icon sprite, so that
     * matching between items with different value counts works correctly.
     */
    private static List<StatLine> tryParseDefenceValues(List<Segment> content, List<Segment> allSegments, Text line) {
        // First validate: content must be only signed values (no text labels)
        for (Segment seg : content) {
            Matcher m = SIGNED_VALUE.matcher(seg.content());
            if (!m.matches() && containsLetter(seg.content())) {
                return null;
            }
        }

        // Walk all segments: track each icon sprite's color, then use it to label the following value.
        // Using icon color as the key ensures matching works even when items show different element subsets.
        List<StatLine> values = new ArrayList<>();
        String lastIconKey = null;
        int fallbackIndex = 0;

        for (int i = 0; i < allSegments.size(); i++) {
            Segment seg = allSegments.get(i);

            // Track icon sprite character content
            if (seg.font().equals(ATTRIBUTE_SPRITE_FONT)) {
                lastIconKey = encodeCodePoints(seg.content());
                continue;
            }

            // Skip other decorative/empty segments
            String stripped = stripNonPrintable(seg.content()).trim();
            if (stripped.isEmpty() || DECORATIVE_SEGMENT_FONTS.contains(seg.font())) continue;

            Matcher m = SIGNED_VALUE.matcher(stripped);
            boolean isUnsigned = !m.matches() && UNSIGNED_NUMBER.matcher(stripped).matches();
            if (m.matches() || isUnsigned) {
                double value = m.matches() ? parseNumber(m.group(1)) : parseNumber(stripped);
                String suffix = m.matches() ? m.group(2) : null;
                boolean isPercent = "%".equals(suffix);
                String key = lastIconKey != null ? lastIconKey : "pos" + fallbackIndex;
                String label = "ElemDef " + key;
                values.add(new StatLine(label, value, 0, isPercent, false, true, false,
                        StatGroup.DEFENCE, line));
                fallbackIndex++;
                lastIconKey = null;
            }
        }

        return values.isEmpty() ? null : values;
    }

    // Requirement lines: icon present, label + value structure
    private static List<StatLine> tryParseRequirement(List<Segment> content, Text line) {
        if (content.size() < 2) return null;

        Segment valueSeg = content.get(content.size() - 1);
        String label = collectLabel(content, 0, content.size() - 1);

        try {
            double value = parseNumber(valueSeg.content());
            return List.of(new StatLine(label, value, 0, false, false, true, false,
                    StatGroup.REQUIREMENT, line));
        } catch (NumberFormatException e) {
            return List.of(new StatLine(label, 0, 0, false, false, true, true,
                    StatGroup.REQUIREMENT, line));
        }
    }

    // Stat/attribute values: signed value pattern (+N, +N%, +N/Ns)
    // Handles both "Label +N" and "+N Label" orderings
    private static List<StatLine> tryParseStatValue(List<Segment> content, Text line) {
        for (int i = 0; i < content.size(); i++) {
            Matcher m = SIGNED_VALUE.matcher(content.get(i).content());
            if (m.matches()) {
                double value = parseNumber(m.group(1));
                String suffix = m.group(2);
                boolean isPercent = "%".equals(suffix);

                // Try label from preceding segments
                String label = collectLabel(content, 0, i);

                // If no preceding label, try following segments (e.g. "+1,800 Health")
                if (label.isEmpty()) {
                    label = collectLabel(content, i + 1, content.size());
                }

                // Label must exist and contain at least one letter
                if (label.isEmpty() || !containsLetter(label)) continue;

                StatGroup group = classifyByLabel(label);
                return List.of(new StatLine(label, value, 0, isPercent, false, true, false,
                        group, line));
            }
        }
        return null;
    }

    private static String collectLabel(List<Segment> content, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            String text = content.get(i).content();
            if (!text.isBlank()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(text);
            }
        }
        return sb.toString().trim();
    }

    private static boolean containsLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) return true;
        }
        return false;
    }

    private static StatGroup classifyByLabel(String label) {
        if (ATTRIBUTE_NAMES.contains(label)) return StatGroup.ATTRIBUTE;
        if (label.equals("Health")) return StatGroup.HEALTH;
        // DEFENCE and AVERAGE_DAMAGE are only assigned by tryParseDefenceValues and
        // tryParseDamageRange (icon-based). Text labels like "Spell Damage", "Earth Defence"
        // in the effects section are classified as STAT.
        return StatGroup.STAT;
    }

    /**
     * Strips all non-printable characters: BMP PUA (U+E000-U+F8FF) and all
     * supplementary characters (U+10000+), which are used for custom font
     * glyphs, spacing, and icons in Wynncraft tooltips.
     */
    private static String stripNonPrintable(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            // Keep only BMP printable characters outside PUA range
            if (cp >= 0x20 && cp < 0xE000) {
                sb.appendCodePoint(cp);
            } else if (cp > 0xF8FF && cp < 0x10000) {
                sb.appendCodePoint(cp);
            }
            // All supplementary characters (U+10000+) and BMP PUA are stripped
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    /**
     * Encodes a string's code points as hex, producing a unique key per icon glyph.
     * Each element in Wynncraft uses a distinct PUA character for its icon.
     */
    private static String encodeCodePoints(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (!sb.isEmpty()) sb.append('.');
            sb.append(Integer.toHexString(cp));
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static double parseNumber(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }
}

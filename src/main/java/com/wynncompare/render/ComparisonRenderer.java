package com.wynncompare.render;

import com.wynncompare.WynnCompareClient;
import com.wynncompare.comparison.ComparisonBuilder;
import com.wynncompare.comparison.LoreParser;
import com.wynncompare.equipment.EquipmentResolver;
import com.wynncompare.item.WynnItemParser;
import com.wynncompare.item.WynnItemType;
import com.wynncompare.mixin.HandledScreenAccessor;
import com.wynncompare.mixin.KeyBindingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.util.InputUtil;
import org.joml.Vector2i;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ComparisonRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("wynncompare");
    private static boolean loggedKeyOnce = false;
    private static final int TOOLTIP_GAP = 8;

    public static void onScreenRender(HandledScreen<?> screen, DrawContext drawContext, int mouseX, int mouseY) {
        try {
            onScreenRenderInner(screen, drawContext, mouseX, mouseY);
        } catch (Exception e) {
            LOGGER.error("[WynnCompare] Error in onScreenRender", e);
        }
    }

    private static void onScreenRenderInner(HandledScreen<?> screen, DrawContext drawContext, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        if (!isCompareKeyHeld(client)) {
            return;
        }

        if (!loggedKeyOnce) {
            LOGGER.info("[WynnCompare] Compare key detected as held");
            loggedKeyOnce = true;
        }

        Slot focusedSlot = ((HandledScreenAccessor) screen).getFocusedSlot();
        if (focusedSlot == null || !focusedSlot.hasStack()) {
            return;
        }

        ItemStack hoveredStack = focusedSlot.getStack();

        WynnItemType type = WynnItemParser.parse(hoveredStack);
        if (type == null) {
            return;
        }

        List<ItemStack> equippedStacks = EquipmentResolver.findEquipped(
                client.player, type, hoveredStack, screen.getScreenHandler());
        if (equippedStacks.isEmpty()) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();

        // Compute Y to match vanilla hovered tooltip position
        int hoveredHeight = computeHoveredTooltipHeight(client, hoveredStack);
        int baseY = computeVanillaTooltipY(mouseY, hoveredHeight, screenHeight);

        // Get hovered item tooltip lines and parse lore
        List<Text> hoveredTooltipLines = hoveredStack.getTooltip(
                Item.TooltipContext.create(client.world),
                client.player,
                TooltipType.ADVANCED
        );
        List<LoreParser.StatLine> hoveredStats = LoreParser.parse(hoveredTooltipLines);

        // Build tooltip data for all equipped items and comparison tooltips
        List<List<Text>> allEquippedTooltipLines = new ArrayList<>();
        List<Integer> allEquippedWidths = new ArrayList<>();
        List<List<Text>> allComparisonTooltipLines = new ArrayList<>();
        List<Integer> allComparisonWidths = new ArrayList<>();

        for (int i = 0; i < equippedStacks.size(); i++) {
            ItemStack equippedStack = equippedStacks.get(i);

            // Equipped tooltip
            List<Text> tooltipLines = new ArrayList<>(equippedStack.getTooltip(
                    Item.TooltipContext.create(client.world),
                    client.player,
                    TooltipType.ADVANCED
            ));

            String header = equippedStacks.size() > 1
                    ? "Equipped (" + (i + 1) + "/" + equippedStacks.size() + "):"
                    : "Equipped:";
            tooltipLines.addFirst(Text.literal(header).formatted(Formatting.GOLD, Formatting.BOLD));

            allEquippedTooltipLines.add(tooltipLines);
            allEquippedWidths.add(computeTooltipWidth(textRenderer, tooltipLines));

            // Comparison tooltip
            List<LoreParser.StatLine> equippedStats = LoreParser.parse(
                    equippedStack.getTooltip(
                            Item.TooltipContext.create(client.world),
                            client.player,
                            TooltipType.ADVANCED
                    )
            );

            List<Text> comparisonLines = new ArrayList<>();
            String equippedName = equippedStack.getName().getString();
            comparisonLines.add(Text.literal("Comparing with").formatted(Formatting.GRAY));
            comparisonLines.add(Text.literal(equippedName).formatted(Formatting.GOLD, Formatting.BOLD));
            comparisonLines.addAll(ComparisonBuilder.build(hoveredStats, equippedStats));

            allComparisonTooltipLines.add(comparisonLines);
            allComparisonWidths.add(computeTooltipWidth(textRenderer, comparisonLines));
        }

        // Total width of all equipped tooltips side by side
        int totalEquippedWidth = 0;
        for (int w : allEquippedWidths) {
            totalEquippedWidth += w;
        }
        totalEquippedWidth += TOOLTIP_GAP * (Math.max(allEquippedWidths.size() - 1, 0));

        int hoveredTooltipWidth = computeTooltipWidth(textRenderer, hoveredTooltipLines);

        // Total width of all comparison tooltips side by side
        int totalComparisonWidth = 0;
        for (int w : allComparisonWidths) {
            totalComparisonWidth += w;
        }
        totalComparisonWidth += TOOLTIP_GAP * (Math.max(allComparisonWidths.size() - 1, 0));

        // Position equipped tooltips to the LEFT of the cursor
        int equippedStartX = mouseX - totalEquippedWidth - 16;
        if (equippedStartX < 4) {
            equippedStartX = 4;
        }

        // Position comparison tooltips to the RIGHT of the hovered tooltip
        int comparisonStartX = mouseX + 12 + hoveredTooltipWidth + TOOLTIP_GAP;
        if (comparisonStartX + totalComparisonWidth > screenWidth - 4) {
            // Try to fit by shifting left, but don't overlap hovered
            comparisonStartX = screenWidth - totalComparisonWidth - 4;
        }

        // Render equipped tooltips (left side)
        int currentX = equippedStartX;
        for (int i = 0; i < allEquippedTooltipLines.size(); i++) {
            List<Text> tooltipLines = allEquippedTooltipLines.get(i);
            int tooltipWidth = allEquippedWidths.get(i);
            renderTooltip(drawContext, textRenderer, tooltipLines, currentX, baseY, screenHeight);
            currentX += tooltipWidth + TOOLTIP_GAP;
        }

        // Render comparison tooltips (right side)
        currentX = comparisonStartX;
        for (int i = 0; i < allComparisonTooltipLines.size(); i++) {
            List<Text> tooltipLines = allComparisonTooltipLines.get(i);
            int tooltipWidth = allComparisonWidths.get(i);
            renderTooltip(drawContext, textRenderer, tooltipLines, currentX, baseY, screenHeight);
            currentX += tooltipWidth + TOOLTIP_GAP;
        }
    }

    private static void renderTooltip(DrawContext drawContext, TextRenderer textRenderer,
                                       List<Text> tooltipLines, int x, int baseY, int screenHeight) {
        int tooltipHeight = computeTooltipHeight(textRenderer, tooltipLines);

        int y = baseY;
        if (y + tooltipHeight > screenHeight - 3) {
            y = screenHeight - tooltipHeight - 3;
        }
        if (y < 3) {
            y = 3;
        }

        List<TooltipComponent> components = tooltipLines.stream()
                .map(Text::asOrderedText)
                .map(TooltipComponent::of)
                .toList();
        final int finalX = x;
        final int finalY = y;
        drawContext.drawTooltipImmediately(textRenderer, components, finalX, finalY,
                (screenW, screenH, posX, posY, w, h) -> new Vector2i(finalX, finalY), null);
    }

    private static boolean isCompareKeyHeld(MinecraftClient client) {
        InputUtil.Key boundKey = ((KeyBindingAccessor) WynnCompareClient.compareKey).getBoundKey();
        return InputUtil.isKeyPressed(client.getWindow(), boundKey.getCode());
    }

    private static int computeTooltipWidth(TextRenderer textRenderer, List<Text> lines) {
        int maxWidth = 0;
        for (Text line : lines) {
            int width = textRenderer.getWidth(line);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        return maxWidth + 8;
    }

    private static int computeTooltipHeight(TextRenderer textRenderer, List<Text> lines) {
        int height = 8;
        for (int i = 0; i < lines.size(); i++) {
            height += textRenderer.fontHeight;
            if (i == 0) {
                height += 2;
            }
        }
        return height;
    }

    private static int computeVanillaTooltipY(int mouseY, int tooltipHeight, int screenHeight) {
        int y = mouseY - 12;
        if (y + tooltipHeight + 3 > screenHeight) {
            y = screenHeight - tooltipHeight - 3;
        }
        if (y < 3) {
            y = 3;
        }
        return y;
    }

    private static int computeHoveredTooltipHeight(MinecraftClient client, ItemStack stack) {
        List<Text> lines = stack.getTooltip(
                Item.TooltipContext.create(client.world),
                client.player,
                TooltipType.ADVANCED
        );
        return computeTooltipHeight(client.textRenderer, lines);
    }
}

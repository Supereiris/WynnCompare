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

        boolean showEquipped = isCompareKeyHeld(client);
        boolean showCompare = isDetailCompareKeyHeld(client);

        if (!showEquipped && !showCompare) {
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
        int screenHeight = drawContext.getScaledWindowHeight();

        // Compute Y to match vanilla hovered tooltip position
        int hoveredHeight = computeHoveredTooltipHeight(client, hoveredStack);
        int baseY = computeVanillaTooltipY(mouseY, hoveredHeight, screenHeight);

        // Get hovered item tooltip lines and parse lore (needed for comparison)
        List<LoreParser.StatLine> hoveredStats = null;
        if (showCompare) {
            List<Text> hoveredTooltipLines = hoveredStack.getTooltip(
                    Item.TooltipContext.create(client.world),
                    client.player,
                    TooltipType.ADVANCED
            );
            hoveredStats = LoreParser.parse(hoveredTooltipLines);
        }

        // Build equipped tooltips (C key)
        List<List<Text>> equippedTooltipLines = new ArrayList<>();
        List<Integer> equippedWidths = new ArrayList<>();
        if (showEquipped) {
            for (int i = 0; i < equippedStacks.size(); i++) {
                ItemStack equippedStack = equippedStacks.get(i);
                List<Text> tooltipLines = new ArrayList<>(equippedStack.getTooltip(
                        Item.TooltipContext.create(client.world),
                        client.player,
                        TooltipType.ADVANCED
                ));

                String header = equippedStacks.size() > 1
                        ? "Equipped (" + (i + 1) + "/" + equippedStacks.size() + "):"
                        : "Equipped:";
                tooltipLines.addFirst(Text.literal(header).formatted(Formatting.GOLD, Formatting.BOLD));

                equippedTooltipLines.add(tooltipLines);
                equippedWidths.add(computeTooltipWidth(textRenderer, tooltipLines));
            }
        }

        // Build comparison tooltips (X key)
        List<List<Text>> compareTooltipLines = new ArrayList<>();
        List<Integer> compareWidths = new ArrayList<>();
        if (showCompare) {
            for (int i = 0; i < equippedStacks.size(); i++) {
                ItemStack equippedStack = equippedStacks.get(i);
                List<LoreParser.StatLine> equippedStats = LoreParser.parse(
                        equippedStack.getTooltip(
                                Item.TooltipContext.create(client.world),
                                client.player,
                                TooltipType.ADVANCED
                        )
                );

                List<Text> comparisonLines = new ArrayList<>();
                String equippedName = equippedStack.getName().getString();
                comparisonLines.add(Text.literal("Comparing based on").formatted(Formatting.GRAY));
                comparisonLines.add(Text.literal(equippedName).formatted(Formatting.GOLD, Formatting.BOLD));
                comparisonLines.add(Text.empty());
                comparisonLines.addAll(ComparisonBuilder.build(hoveredStats, equippedStats));

                compareTooltipLines.add(comparisonLines);
                compareWidths.add(computeTooltipWidth(textRenderer, comparisonLines));
            }
        }

        // Compare (X) takes priority over equipped (C) when both are held
        List<List<Text>> allTooltipLines = showCompare ? compareTooltipLines : equippedTooltipLines;
        List<Integer> allWidths = showCompare ? compareWidths : equippedWidths;

        // Total width of all tooltips side by side
        int totalWidth = 0;
        for (int w : allWidths) {
            totalWidth += w;
        }
        totalWidth += TOOLTIP_GAP * (Math.max(allWidths.size() - 1, 0));

        // Position tooltips to the LEFT of the cursor
        int startX = mouseX - totalWidth - 16;
        if (startX < 4) {
            startX = 4;
        }

        // Render all tooltips (left side)
        int currentX = startX;
        for (int i = 0; i < allTooltipLines.size(); i++) {
            List<Text> tooltipLines = allTooltipLines.get(i);
            int tooltipWidth = allWidths.get(i);
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

    private static boolean isDetailCompareKeyHeld(MinecraftClient client) {
        InputUtil.Key boundKey = ((KeyBindingAccessor) WynnCompareClient.detailCompareKey).getBoundKey();
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

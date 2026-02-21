package com.wynncompare.render;

import com.wynncompare.WynnCompareClient;
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
    private static final int TOOLTIP_GAP = 4;

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
        LOGGER.info("[WynnCompare] Hovering item: {}", hoveredStack.getName().getString());

        WynnItemType type = WynnItemParser.parse(hoveredStack);
        if (type == null) {
            LOGGER.info("[WynnCompare] Item is not a recognized Wynn item");
            return;
        }
        LOGGER.info("[WynnCompare] Detected Wynn item type: {}", type);

        List<ItemStack> equippedStacks = EquipmentResolver.findEquipped(
                client.player, type, hoveredStack, screen.getScreenHandler());
        if (equippedStacks.isEmpty()) {
            LOGGER.info("[WynnCompare] No equipped equivalent found for type {}", type);
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();

        // Compute Y to match vanilla hovered tooltip position
        int hoveredHeight = computeHoveredTooltipHeight(client, hoveredStack);
        int baseY = computeVanillaTooltipY(mouseY, hoveredHeight, screenHeight);

        int currentY = baseY;
        for (int i = 0; i < equippedStacks.size(); i++) {
            ItemStack equippedStack = equippedStacks.get(i);
            LOGGER.info("[WynnCompare] Found equipped item {}: {}", i + 1, equippedStack.getName().getString());

            List<Text> tooltipLines = new ArrayList<>(equippedStack.getTooltip(
                    Item.TooltipContext.create(client.world),
                    client.player,
                    TooltipType.ADVANCED
            ));

            String header = equippedStacks.size() > 1
                    ? "Equipped (" + (i + 1) + "/" + equippedStacks.size() + "):"
                    : "Equipped:";
            tooltipLines.addFirst(Text.literal(header).formatted(Formatting.GOLD, Formatting.BOLD));

            int tooltipWidth = computeTooltipWidth(textRenderer, tooltipLines);
            int tooltipHeight = computeTooltipHeight(textRenderer, tooltipLines);

            // X positioning: try left of cursor, fall back to right
            int x = mouseX - tooltipWidth - 16;
            if (x < 4) {
                x = mouseX + 16 + estimateHoveredTooltipWidth(client, hoveredStack);
                if (x + tooltipWidth > screenWidth - 4) {
                    x = mouseX + 16;
                }
            }

            // Clamp Y to screen
            int y = currentY;
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

            // Stack next tooltip below this one
            currentY = y + tooltipHeight + TOOLTIP_GAP;
        }
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

    private static int estimateHoveredTooltipWidth(MinecraftClient client, ItemStack stack) {
        List<Text> hoveredLines = stack.getTooltip(
                Item.TooltipContext.create(client.world),
                client.player,
                TooltipType.ADVANCED
        );
        return computeTooltipWidth(client.textRenderer, hoveredLines);
    }
}

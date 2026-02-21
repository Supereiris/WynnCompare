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

        // Build tooltip data for all equipped items
        List<List<Text>> allTooltipLines = new ArrayList<>();
        List<Integer> allWidths = new ArrayList<>();
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

            allTooltipLines.add(tooltipLines);
            allWidths.add(computeTooltipWidth(textRenderer, tooltipLines));
        }

        // Total width of all equipped tooltips side by side
        int totalEquippedWidth = 0;
        for (int w : allWidths) {
            totalEquippedWidth += w;
        }
        totalEquippedWidth += TOOLTIP_GAP * (allWidths.size() - 1);

        int hoveredTooltipWidth = estimateHoveredTooltipWidth(client, hoveredStack);

        // Position all equipped tooltips to the LEFT of the cursor
        // Start X: try to fit all tooltips to the left of the hovered tooltip
        int startX = mouseX - totalEquippedWidth - 16;
        if (startX < 4) {
            // Not enough space on the left, place to the right
            startX = mouseX + 16 + hoveredTooltipWidth;
            if (startX + totalEquippedWidth > screenWidth - 4) {
                startX = mouseX + 16;
            }
        }

        // Render each tooltip side by side horizontally
        int currentX = startX;
        for (int i = 0; i < allTooltipLines.size(); i++) {
            List<Text> tooltipLines = allTooltipLines.get(i);
            int tooltipWidth = allWidths.get(i);
            int tooltipHeight = computeTooltipHeight(textRenderer, tooltipLines);

            // Clamp Y to screen
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
            final int finalX = currentX;
            final int finalY = y;
            drawContext.drawTooltipImmediately(textRenderer, components, finalX, finalY,
                    (screenW, screenH, posX, posY, w, h) -> new Vector2i(finalX, finalY), null);

            currentX += tooltipWidth + TOOLTIP_GAP;
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

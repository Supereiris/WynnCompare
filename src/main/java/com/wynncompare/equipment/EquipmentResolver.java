package com.wynncompare.equipment;

import com.wynncompare.item.WynnItemParser;
import com.wynncompare.item.WynnItemType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EquipmentResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("wynncompare");

    // Wynncraft screen handler accessory slots (from inventory debug dump)
    private static final int RING_SLOT_1 = 9;
    private static final int RING_SLOT_2 = 10;
    private static final int BRACELET_SLOT = 11;
    private static final int NECKLACE_SLOT = 12;

    // All accessory slot indices
    private static final int[] ALL_ACCESSORY_SLOTS = { RING_SLOT_1, RING_SLOT_2, BRACELET_SLOT, NECKLACE_SLOT };

    public static List<ItemStack> findEquipped(ClientPlayerEntity player, WynnItemType type,
                                                ItemStack hoveredStack, ScreenHandler handler) {
        if (player == null || type == null) {
            return Collections.emptyList();
        }

        if (type.isAccessory()) {
            return findAccessories(type, hoveredStack, handler);
        }

        if (type.isWeapon()) {
            ItemStack inHand = player.getMainHandStack();
            if (inHand == null || inHand.isEmpty() || ItemStack.areEqual(inHand, hoveredStack)) {
                return Collections.emptyList();
            }
            // Verify the held item is also a Wynn weapon
            if (!WynnItemParser.isWynnItem(inHand)) {
                return Collections.emptyList();
            }
            return List.of(inHand);
        }

        // Armor
        ItemStack result = switch (type) {
            case HELMET -> player.getEquippedStack(EquipmentSlot.HEAD);
            case CHESTPLATE -> player.getEquippedStack(EquipmentSlot.CHEST);
            case LEGGINGS -> player.getEquippedStack(EquipmentSlot.LEGS);
            case BOOTS -> player.getEquippedStack(EquipmentSlot.FEET);
            default -> null;
        };

        if (result == null || result.isEmpty() || ItemStack.areEqual(result, hoveredStack)) {
            return Collections.emptyList();
        }

        return List.of(result);
    }

    private static List<ItemStack> findAccessories(WynnItemType type, ItemStack hoveredStack,
                                                    ScreenHandler handler) {
        int[] slots = switch (type) {
            case RING -> new int[]{ RING_SLOT_1, RING_SLOT_2 };
            case BRACELET -> new int[]{ BRACELET_SLOT };
            case NECKLACE -> new int[]{ NECKLACE_SLOT };
            // Generic ACCESSORY — can't determine subtype, so check all accessory slots
            case ACCESSORY -> ALL_ACCESSORY_SLOTS;
            default -> new int[0];
        };

        List<ItemStack> found = new ArrayList<>();
        for (int slotIndex : slots) {
            if (slotIndex >= handler.slots.size()) {
                continue;
            }
            ItemStack stack = handler.slots.get(slotIndex).getStack();
            if (!stack.isEmpty() && !ItemStack.areEqual(stack, hoveredStack)) {
                found.add(stack);
            }
        }
        return found;
    }
}

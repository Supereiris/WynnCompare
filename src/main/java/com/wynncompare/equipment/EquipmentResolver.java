package com.wynncompare.equipment;

import com.wynncompare.item.WynnItemParser;
import com.wynncompare.item.WynnItemType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public class EquipmentResolver {

    public static ItemStack findEquipped(ClientPlayerEntity player, WynnItemType type, ItemStack hoveredStack) {
        if (player == null || type == null) {
            return null;
        }

        ItemStack result = switch (type) {
            case HELMET -> player.getEquippedStack(EquipmentSlot.HEAD);
            case CHESTPLATE -> player.getEquippedStack(EquipmentSlot.CHEST);
            case LEGGINGS -> player.getEquippedStack(EquipmentSlot.LEGS);
            case BOOTS -> player.getEquippedStack(EquipmentSlot.FEET);
            case WAND, DAGGER, BOW, SPEAR, RELIK -> player.getMainHandStack();
            case RING, BRACELET, NECKLACE -> findAccessory(player, type, hoveredStack);
        };

        if (result == null || result.isEmpty()) {
            return null;
        }

        // Don't compare an item to itself
        if (ItemStack.areEqual(result, hoveredStack)) {
            return null;
        }

        // Verify the equipped item is also a Wynn item of the same type
        WynnItemType equippedType = WynnItemParser.parse(result);
        if (equippedType != type) {
            return null;
        }

        return result;
    }

    private static ItemStack findAccessory(ClientPlayerEntity player, WynnItemType type, ItemStack hoveredStack) {
        // Scan entire inventory for matching accessory type
        // Wynncraft uses server-side custom slots, so we search by type
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || ItemStack.areEqual(stack, hoveredStack)) {
                continue;
            }
            WynnItemType parsedType = WynnItemParser.parse(stack);
            if (parsedType == type) {
                return stack;
            }
        }
        return null;
    }
}

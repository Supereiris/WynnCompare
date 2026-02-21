package com.wynncompare.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShearsItem;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Set;

public class WynnItemParser {

    private static final Set<String> RARITY_MARKERS = Set.of(
            "Normal Item",
            "Unique Item",
            "Rare Item",
            "Legendary Item",
            "Fabled Item",
            "Mythic Item",
            "Set Item"
    );

    public static WynnItemType parse(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        if (!isWynnItem(stack)) {
            return null;
        }

        // Layer 2: Equipment component for armor detection
        WynnItemType equipType = fromEquipmentComponent(stack);
        if (equipType != null) {
            return equipType;
        }

        // Layer 2b: Vanilla base item mapping for weapons
        WynnItemType vanillaType = fromVanillaItem(stack);
        if (vanillaType != null) {
            return vanillaType;
        }

        // Layer 3: Lore scanning for accessories
        return fromLore(stack);
    }

    private static boolean isWynnItem(ItemStack stack) {
        LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
        if (loreComponent == null) {
            return false;
        }

        List<Text> lines = loreComponent.lines();
        for (Text line : lines) {
            String plainText = line.getString();
            for (String marker : RARITY_MARKERS) {
                if (plainText.contains(marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static WynnItemType fromEquipmentComponent(ItemStack stack) {
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null) {
            return null;
        }

        return switch (equippable.slot()) {
            case HEAD -> WynnItemType.HELMET;
            case CHEST -> WynnItemType.CHESTPLATE;
            case LEGS -> WynnItemType.LEGGINGS;
            case FEET -> WynnItemType.BOOTS;
            default -> null;
        };
    }

    private static WynnItemType fromVanillaItem(ItemStack stack) {
        // Weapon detection via known Wynncraft base items
        if (stack.isOf(Items.IRON_SHOVEL)) return WynnItemType.SPEAR;
        if (stack.isOf(Items.WOODEN_SHOVEL)) return WynnItemType.WAND;
        if (stack.isOf(Items.STONE_SHOVEL)) return WynnItemType.RELIK;
        if (stack.getItem() instanceof ShearsItem) return WynnItemType.DAGGER;
        if (stack.getItem() instanceof BowItem) return WynnItemType.BOW;

        return null;
    }

    private static WynnItemType fromLore(ItemStack stack) {
        LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
        if (loreComponent == null) {
            return null;
        }

        for (Text line : loreComponent.lines()) {
            String plainText = line.getString();
            // Accessory type keywords typically appear in lore as type identifiers
            if (plainText.contains("Ring")) return WynnItemType.RING;
            if (plainText.contains("Bracelet")) return WynnItemType.BRACELET;
            if (plainText.contains("Necklace")) return WynnItemType.NECKLACE;
        }

        return null;
    }
}

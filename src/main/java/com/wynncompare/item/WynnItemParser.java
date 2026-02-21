package com.wynncompare.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class WynnItemParser {

    private static final Logger LOGGER = LoggerFactory.getLogger("wynncompare");

    private static final Set<String> RARITY_MARKERS = Set.of(
            "Normal Item",
            "Unique Item",
            "Rare Item",
            "Legendary Item",
            "Fabled Item",
            "Mythic Item",
            "Set Item"
    );

    /**
     * Parse a Wynncraft item type from an ItemStack.
     * Uses CustomModelData (like Wynntils), equipment component, and lore patterns.
     */
    public static WynnItemType parse(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        if (!isWynnItem(stack)) {
            return null;
        }

        // Armor: detect via equipment component
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable != null) {
            WynnItemType equipType = switch (equippable.slot()) {
                case HEAD -> WynnItemType.HELMET;
                case CHEST -> WynnItemType.CHESTPLATE;
                case LEGS -> WynnItemType.LEGGINGS;
                case FEET -> WynnItemType.BOOTS;
                default -> null;
            };
            if (equipType != null) {
                return equipType;
            }
        }

        // Weapons: detect by vanilla base item (weapons use unique base items)
        WynnItemType weaponType = fromWeaponBaseItem(stack);
        if (weaponType != null) {
            return weaponType;
        }

        // For potion-based items: use lore to distinguish weapon vs accessory
        if (stack.isOf(Items.POTION)) {
            if (hasAttackSpeed(stack)) {
                return WynnItemType.WEAPON;
            }
            // It's an accessory but we can't tell ring/bracelet/necklace from item data alone
            return WynnItemType.ACCESSORY;
        }

        LOGGER.info("[WynnCompare] Wynn item not mapped to type, base item: {}", stack.getItem());
        return null;
    }

    public static boolean isWynnItem(ItemStack stack) {
        LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
        if (loreComponent == null) {
            return false;
        }

        for (Text line : loreComponent.lines()) {
            String plainText = stripSectionCodes(line.getString());
            for (String marker : RARITY_MARKERS) {
                if (plainText.contains(marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Weapons that still use unique vanilla base items (crafted weapons).
     */
    private static WynnItemType fromWeaponBaseItem(ItemStack stack) {
        if (stack.isOf(Items.IRON_SHOVEL)) return WynnItemType.SPEAR;
        if (stack.isOf(Items.WOODEN_SHOVEL)) return WynnItemType.WAND;
        if (stack.isOf(Items.STONE_SHOVEL)) return WynnItemType.RELIK;
        if (stack.isOf(Items.SHEARS)) return WynnItemType.DAGGER;
        if (stack.isOf(Items.BOW)) return WynnItemType.BOW;
        return null;
    }

    /**
     * Check if the item lore contains an "Attack Speed" line (weapon indicator).
     */
    private static boolean hasAttackSpeed(ItemStack stack) {
        LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
        if (loreComponent == null) {
            return false;
        }

        for (Text line : loreComponent.lines()) {
            String plainText = stripSectionCodes(line.getString());
            if (plainText.contains("Attack Speed")) {
                return true;
            }
        }
        return false;
    }

    private static String stripSectionCodes(String text) {
        return text.replaceAll("§.", "");
    }
}

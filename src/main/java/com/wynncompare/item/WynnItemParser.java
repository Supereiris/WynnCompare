package com.wynncompare.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Set;

public class WynnItemParser {

    // Tooltip style identifiers used by Wynncraft for item rarities
    private static final Set<String> RARITY_STYLES = Set.of(
            "normal", "unique", "rare", "legendary", "fabled", "mythic", "set"
    );

    /**
     * Parse a Wynncraft item type from an ItemStack.
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

        // CustomModelData-based detection (weapons and accessories)
        WynnItemType cmdType = fromCustomModelData(stack);
        if (cmdType != null) {
            return cmdType;
        }

        // Fallback: crafted weapons use unique vanilla base items
        WynnItemType weaponType = fromWeaponBaseItem(stack);
        if (weaponType != null) {
            return weaponType;
        }

        // Fallback for potion items without CustomModelData
        if (stack.isOf(Items.POTION)) {
            if (hasAttackSpeed(stack)) {
                return WynnItemType.WEAPON;
            }
            return WynnItemType.ACCESSORY;
        }

        return null;
    }

    public static boolean isWynnItem(ItemStack stack) {
        // Primary: check tooltip_style component for Wynncraft rarity identifiers
        Identifier tooltipStyle = stack.get(DataComponentTypes.TOOLTIP_STYLE);
        if (tooltipStyle != null && RARITY_STYLES.contains(tooltipStyle.getPath())) {
            return true;
        }

        // Fallback: check custom_model_data strings for item_tier_* prefix
        CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (cmd != null) {
            for (String s : cmd.strings()) {
                if (s.startsWith("item_tier_")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static WynnItemType fromCustomModelData(ItemStack stack) {
        CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (cmd == null) {
            return null;
        }

        List<Float> floats = cmd.floats();
        if (floats.isEmpty()) {
            return null;
        }

        for (float value : floats) {
            WynnItemType type = ModelDataRanges.typeOf(value);
            if (type != null) return type;
        }

        return null;
    }

    private static WynnItemType fromWeaponBaseItem(ItemStack stack) {
        if (stack.isOf(Items.IRON_SHOVEL)) return WynnItemType.SPEAR;
        if (stack.isOf(Items.WOODEN_SHOVEL)) return WynnItemType.WAND;
        if (stack.isOf(Items.STONE_SHOVEL)) return WynnItemType.RELIK;
        if (stack.isOf(Items.SHEARS)) return WynnItemType.DAGGER;
        if (stack.isOf(Items.BOW)) return WynnItemType.BOW;
        return null;
    }

    private static boolean hasAttackSpeed(ItemStack stack) {
        LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
        if (loreComponent == null) {
            return false;
        }

        for (Text line : loreComponent.lines()) {
            String plainText = line.getString();
            if (plainText.contains("DPS") || plainText.contains("hits/s")) {
                return true;
            }
        }
        return false;
    }
}

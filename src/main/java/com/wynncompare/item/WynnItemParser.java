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

    public static final Set<String> RARITY_MARKERS = Set.of(
            "Normal Item",
            "Unique Item",
            "Rare Item",
            "Legendary Item",
            "Fabled Item",
            "Mythic Item",
            "Set Item"
    );

    // CustomModelData float ranges from Wynntils' model_data.json (cdn.wynntils.com)
    private static final float RING_MIN = 1361f;
    private static final float RING_MAX = 1377f;
    private static final float BRACELET_MIN = 1378f;
    private static final float BRACELET_MAX = 1391f;
    private static final float NECKLACE_MIN = 1392f;
    private static final float NECKLACE_MAX = 1408f;
    private static final float BOW_MIN = 1409f;
    private static final float BOW_MAX = 1502f;
    private static final float DAGGER_MIN = 1503f;
    private static final float DAGGER_MAX = 1599f;
    private static final float WAND_MIN = 1600f;
    private static final float WAND_MAX = 1695f;
    private static final float RELIK_MIN = 1696f;
    private static final float RELIK_MAX = 1789f;
    private static final float SPEAR_MIN = 1790f;
    private static final float SPEAR_MAX = 1884f;

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
            if (inRange(value, RING_MIN, RING_MAX)) return WynnItemType.RING;
            if (inRange(value, BRACELET_MIN, BRACELET_MAX)) return WynnItemType.BRACELET;
            if (inRange(value, NECKLACE_MIN, NECKLACE_MAX)) return WynnItemType.NECKLACE;
            if (inRange(value, BOW_MIN, BOW_MAX)) return WynnItemType.BOW;
            if (inRange(value, DAGGER_MIN, DAGGER_MAX)) return WynnItemType.DAGGER;
            if (inRange(value, WAND_MIN, WAND_MAX)) return WynnItemType.WAND;
            if (inRange(value, RELIK_MIN, RELIK_MAX)) return WynnItemType.RELIK;
            if (inRange(value, SPEAR_MIN, SPEAR_MAX)) return WynnItemType.SPEAR;
        }

        return null;
    }

    private static boolean inRange(float value, float min, float max) {
        return value >= min && value <= max;
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

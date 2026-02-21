package com.wynncompare.item;

public enum WynnItemType {
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS,
    WAND,
    DAGGER,
    BOW,
    SPEAR,
    RELIK,
    WEAPON,      // generic weapon (couldn't determine specific type)
    RING,
    BRACELET,
    NECKLACE,
    ACCESSORY;   // generic accessory (couldn't determine ring/bracelet/necklace)

    public boolean isArmor() {
        return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
    }

    public boolean isWeapon() {
        return this == WAND || this == DAGGER || this == BOW || this == SPEAR || this == RELIK || this == WEAPON;
    }

    public boolean isAccessory() {
        return this == RING || this == BRACELET || this == NECKLACE || this == ACCESSORY;
    }
}

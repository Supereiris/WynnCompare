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
    RING,
    BRACELET,
    NECKLACE;

    public boolean isArmor() {
        return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
    }

    public boolean isWeapon() {
        return this == WAND || this == DAGGER || this == BOW || this == SPEAR || this == RELIK;
    }

    public boolean isAccessory() {
        return this == RING || this == BRACELET || this == NECKLACE;
    }
}

# WynnCompare

<img src="src/main/resources/assets/wynncompare/icon.png" alt="WynnCompare icon" width="128" align="right">

A client-side Fabric mod for Minecraft 1.21.11 that adds equipment tooltip comparison for [Wynncraft](https://wynncraft.com/). Hold a key while hovering over an item to see your equipped gear or a stat-by-stat breakdown.

## Features

- **Equipped Tooltip (C)** — View your currently equipped item's tooltip alongside the hovered item
- **Stat Comparison (X)** — See a stat-by-stat diff with color-coded improvements and downgrades
- **Smart Positioning** — Tooltips stay within screen bounds
- **Configurable Keybindings** — Rebind keys in Options > Controls under the **WynnCompare** category

Supports all Wynncraft equipment: armor, weapons, and accessories (rings, bracelets, necklaces). Works in your inventory as well as banks and other containers.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.16.0+
- Fabric API
- Java 21+
- [Wynntils](https://modrinth.com/mod/wynntils) *(optional, recommended)*

WynnCompare identifies weapon and accessory types from Wynncraft's item model data, which changes whenever Wynncraft updates its resource pack. When Wynntils is installed, WynnCompare reads the up-to-date values from Wynntils' cache, so detection keeps working after Wynncraft updates. Without Wynntils it falls back to built-in values.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `mods/` folder
3. Download the latest `wynncompare-*.jar` from [Releases](https://github.com/Supereiris/WynnCompare/releases) and place it in your `mods/` folder
4. Launch Minecraft

## Usage

While in a Wynncraft inventory screen:

| Key | Action |
|-----|--------|
| Hold **C** | Show equipped item tooltip on the left |
| Hold **X** | Show stat comparison on the left |

When both keys are held, the comparison tooltip takes priority.

## Known Issues

- Compare keys bound to mouse buttons are not detected; bind them to keyboard keys.

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

For development with [Prism Launcher](https://prismlauncher.org/), `deploy.ps1` builds the mod and installs it into a Prism instance:

```powershell
.\deploy.ps1                      # build and install into the "Wynncraft" instance
.\deploy.ps1 -Launch              # ...and launch it
.\deploy.ps1 -Instance "MyPack"   # use a different instance
```

## License

[MIT](LICENSE)

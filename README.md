# WynnCompare

A client-side Fabric mod for Minecraft 1.21.11 that adds side-by-side tooltip comparison for [Wynncraft](https://wynncraft.com/) equipment. Hold a key while hovering over an item to compare it with your currently equipped gear.

## Requirements

- Java 21+
- Minecraft 1.21.11
- Fabric Loader 0.16.0+
- Fabric API

## Build

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `mods/` folder
3. Copy the WynnCompare JAR from `build/libs/` into your `mods/` folder
4. Launch Minecraft

## Usage

While in a Wynncraft inventory screen, hold the **C** key and hover over a piece of equipment to see a side-by-side comparison tooltip with your currently equipped item.

## License

[MIT](LICENSE)

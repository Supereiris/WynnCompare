# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WynnCompare is a Minecraft Fabric mod project (Java). The project is in early development — source code has not been scaffolded yet.

## Agent Skills

Two skills are available and should be invoked when relevant:

- **`minecraft-fabric-dev`** — Use for all Fabric mod development tasks: setting up the mod, working with mixins, access wideners, porting from Forge/NeoForge, or accessing Minecraft source code via MCP servers.
- **`java-maven-gradle`** — Use for build configuration, dependency management, plugin setup, and CI/CD.

## Build System

Fabric mods conventionally use Gradle with Kotlin DSL (`build.gradle.kts`). Use the `java-maven-gradle` skill when setting up or modifying build files.

## Minecraft/Fabric Development Workflow

Before any Minecraft development task:

1. Sync Fabric docs: `sync_fabric_docs`
2. Identify target version: `list_fabric_versions`, `list_minecraft_versions`
3. Decompile target Minecraft version if needed: `decompile_minecraft_version(version, mapping: "yarn")`

**Mappings:** Use **yarn** for all Fabric development (human-readable, community-supported). Use **mojmap** only for referencing vanilla code. Intermediary is used internally by Fabric for stability across versions.

**Minecraft version note:** Starting with experimental snapshots after 1.21.11, Minecraft releases de-obfuscated builds. For 1.21.11 and earlier, official = obfuscated; always use yarn/mojmap for development.

## Standard Gradle Commands (once project is scaffolded)

```bash
./gradlew build          # Compile and package the mod JAR
./gradlew test           # Run tests
./gradlew runClient      # Launch Minecraft client with the mod
./gradlew runServer      # Launch Minecraft server with the mod
./gradlew dependencies   # Show dependency tree
```

## Key Fabric Project Files (to be created)

- `build.gradle.kts` — Gradle build script with Fabric Loom plugin
- `gradle/libs.versions.toml` — Version catalog for dependencies
- `src/main/resources/fabric.mod.json` — Mod metadata and entrypoints
- `src/main/resources/mixins.json` — Mixin configuration (if using mixins)

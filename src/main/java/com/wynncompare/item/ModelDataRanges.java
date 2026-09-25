package com.wynncompare.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * CustomModelData float ranges used by Wynncraft to identify weapon and accessory types.
 *
 * Wynncraft reshuffles these whenever its resource pack changes, so the live ranges are read
 * from Wynntils' cached copy of model_data.json (static.wynntils.com) when available.
 * The defaults below are only a fallback for players without Wynntils.
 */
public final class ModelDataRanges {

    private static final Logger LOGGER = LoggerFactory.getLogger("wynncompare");

    private static final Path WYNNTILS_CACHE = Path.of("wynntils", "cache", "dataStaticModelData");

    // Fallback ranges, from model_data.json as of the Fruma update
    private static final Map<WynnItemType, float[]> DEFAULTS = new EnumMap<>(Map.of(
            WynnItemType.RING, new float[]{ 3745f, 3761f },
            WynnItemType.BRACELET, new float[]{ 396f, 409f },
            WynnItemType.NECKLACE, new float[]{ 2798f, 2814f },
            WynnItemType.BOW, new float[]{ 294f, 395f },
            WynnItemType.DAGGER, new float[]{ 781f, 886f },
            WynnItemType.WAND, new float[]{ 4274f, 4377f },
            WynnItemType.RELIK, new float[]{ 3643f, 3744f },
            WynnItemType.SPEAR, new float[]{ 3801f, 3903f }
    ));

    private static Map<WynnItemType, float[]> ranges;

    private ModelDataRanges() {}

    /**
     * Returns the item type whose model range contains the given CustomModelData float, or null.
     */
    public static WynnItemType typeOf(float value) {
        for (Map.Entry<WynnItemType, float[]> entry : getRanges().entrySet()) {
            float[] range = entry.getValue();
            if (value >= range[0] && value <= range[1]) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Map<WynnItemType, float[]> getRanges() {
        if (ranges == null) {
            ranges = load();
        }
        return ranges;
    }

    private static Map<WynnItemType, float[]> load() {
        Map<WynnItemType, float[]> result = new EnumMap<>(DEFAULTS);
        Path file = FabricLoader.getInstance().getGameDir().resolve(WYNNTILS_CACHE);
        if (!Files.isRegularFile(file)) {
            LOGGER.info("[WynnCompare] Wynntils model data cache not found, using built-in model ranges");
            return result;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject rangesJson = json.getAsJsonObject("ranges");
            if (rangesJson == null) {
                return result;
            }

            int loaded = 0;
            for (WynnItemType type : DEFAULTS.keySet()) {
                JsonElement element = rangesJson.get(type.name().toLowerCase());
                if (element == null || !element.isJsonArray()) continue;
                JsonArray array = element.getAsJsonArray();
                if (array.size() != 2) continue;
                result.put(type, new float[]{ array.get(0).getAsFloat(), array.get(1).getAsFloat() });
                loaded++;
            }
            LOGGER.info("[WynnCompare] Loaded {} model ranges from Wynntils cache", loaded);
        } catch (Exception e) {
            LOGGER.warn("[WynnCompare] Failed to read Wynntils model data cache, using built-in model ranges", e);
        }
        return result;
    }
}

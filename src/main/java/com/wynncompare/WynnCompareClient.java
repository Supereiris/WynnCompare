package com.wynncompare;

import com.wynncompare.render.ComparisonRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WynnCompareClient implements ClientModInitializer {

    public static final String MOD_ID = "wynncompare";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final KeyBinding.Category KEYBIND_CATEGORY =
            KeyBinding.Category.create(Identifier.of(MOD_ID, "keybinds"));

    public static KeyBinding compareKey;
    public static KeyBinding detailCompareKey;

    @Override
    public void onInitializeClient() {
        compareKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynncompare.compare",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                KEYBIND_CATEGORY
        ));

        detailCompareKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynncompare.detail_compare",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                KEYBIND_CATEGORY
        ));

        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?> handledScreen) {
                ScreenEvents.afterRender(screen).register((screen1, drawContext, mouseX, mouseY, tickDelta) -> {
                    ComparisonRenderer.onScreenRender(handledScreen, drawContext, mouseX, mouseY);
                });
            }
        });

        LOGGER.info("WynnCompare initialized");
    }
}

package com.wynncompare;

import com.wynncompare.render.ComparisonRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WynnCompareClient implements ClientModInitializer {

    public static final String MOD_ID = "wynncompare";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyBinding compareKey;

    @Override
    public void onInitializeClient() {
        compareKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynncompare.compare",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                KeyBinding.Category.MISC
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

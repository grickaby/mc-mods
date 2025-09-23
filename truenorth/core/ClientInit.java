// src/main/java/com/yourname/truenorth/client/ClientInit.java
package com.yourname.truenorth.client;

import com.yourname.truenorth.TrueNorth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.minecraft.resources.ResourceLocation;

@Mod.EventBusSubscriber(modid = TrueNorth.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientInit {
    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(TrueNorth.MODID, "hud");

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent e) {
        // Above vanilla hotbar and chat
        e.registerAboveAll(LAYER_ID, HudOverlay::render);
    }
}

package com.grickaby.truenorth.client;

import com.grickaby.truenorth.TrueNorth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber; 
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.resources.ResourceLocation;

@EventBusSubscriber(modid = TrueNorth.MODID, value = Dist.CLIENT) 
public final class ClientInit {
    public static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath(TrueNorth.MODID, "hud");

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(LAYER_ID, HudOverlay::render);
    }
}

package com.github.alexthe668.domesticationinnovation.client;

import com.github.alexthe668.domesticationinnovation.server.CommonProxy;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The only bridge from common startup code into client-only classes. Every
 * method here DECLARES common types, so DomesticationMod's constant pool and
 * stack maps never mention ClientProxy: a dedicated server can verify and run
 * the mod constructor without RuntimeDistCleaner rejecting the stripped
 * client classes. This class itself is only ever executed behind a
 * dist == CLIENT check, so it never loads on a server.
 */
public final class DIClientFactory {

    private DIClientFactory() {
    }

    public static CommonProxy createProxy() {
        return new ClientProxy();
    }

    /**
     * Client-side startup wiring: mod-bus listeners, the in-game config
     * screen, and the game-bus handlers. The game bus rejects listener
     * objects whose SUPERTYPE declares @SubscribeEvent methods, so the
     * ClientProxy instance is never bus-registered directly: common handlers
     * run on a plain CommonProxy and the client's own two handlers attach by
     * method reference. (CommonProxy keeps all state in statics, so the
     * extra instance is safe.)
     */
    public static void wireClientStartup(IEventBus modEventBus, ModContainer modContainer, CommonProxy proxy) {
        ClientProxy clientProxy = (ClientProxy) proxy;
        ClientProxy.registerModEvents(modEventBus);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.register(new CommonProxy());
        NeoForge.EVENT_BUS.addListener(clientProxy::renderNametagEvent);
        NeoForge.EVENT_BUS.addListener(clientProxy::onAttackEntityFromClient);
    }
}

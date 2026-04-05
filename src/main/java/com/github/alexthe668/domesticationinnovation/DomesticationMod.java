package com.github.alexthe668.domesticationinnovation;

import com.github.alexthe668.domesticationinnovation.client.ClientProxy;
import com.github.alexthe668.domesticationinnovation.server.CommonProxy;
import com.github.alexthe668.domesticationinnovation.server.block.DIBlockRegistry;
import com.github.alexthe668.domesticationinnovation.server.block.DITileEntityRegistry;
import com.github.alexthe668.domesticationinnovation.server.enchantment.DIEnchantmentKeys;
import com.github.alexthe668.domesticationinnovation.server.entity.DIAttachments;
import com.github.alexthe668.domesticationinnovation.server.entity.DIActivityRegistry;
import com.github.alexthe668.domesticationinnovation.server.entity.DIEntityRegistry;
import com.github.alexthe668.domesticationinnovation.server.entity.DIVillagerRegistry;
import com.github.alexthe668.domesticationinnovation.server.item.DIItemRegistry;
import com.github.alexthe668.domesticationinnovation.server.misc.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DomesticationMod.MODID)
public class DomesticationMod {
    public static final String MODID = "domesticationinnovation";
    public static final Logger LOGGER = LogManager.getLogger();
    public static CommonProxy PROXY;
    public static DIConfig CONFIG;

    public DomesticationMod(IEventBus modEventBus, ModContainer modContainer) {
        // Initialize proxy based on dist - replaces removed DistExecutor
        PROXY = FMLEnvironment.dist == Dist.CLIENT ? new ClientProxy() : new CommonProxy();

        modEventBus.addListener(this::setupClient);
        modEventBus.addListener(this::setup);

        // Register client-side mod bus events (particles, layers, etc.)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientProxy.registerModEvents(modEventBus);
        }

        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, DIConfig.CONFIG_SPEC, "domestication-innovation.toml");

        // Convenience accessor
        CONFIG = DIConfig.INSTANCE;

        // Register all deferred registries
        DIItemRegistry.DEF_REG.register(modEventBus);
        DIBlockRegistry.DEF_REG.register(modEventBus);
        DITileEntityRegistry.DEF_REG.register(modEventBus);
        DIEntityRegistry.DEF_REG.register(modEventBus);
        DIPOIRegistry.DEF_REG.register(modEventBus);
        DIParticleRegistry.DEF_REG.register(modEventBus);
        DIVillagerRegistry.DEF_REG.register(modEventBus);
        DISoundRegistry.DEF_REG.register(modEventBus);
        DIActivityRegistry.DEF_REG.register(modEventBus);
        DICreativeTabRegistry.DEF_REG.register(modEventBus);
        DILootRegistry.DEF_REG.register(modEventBus);
        DIAttachments.DEF_REG.register(modEventBus);
        // Note: Enchantments are now data-driven in 1.21.1 - see data/domesticationinnovation/enchantment/
        // DIVillagePieceRegistry is handled in common setup

        NeoForge.EVENT_BUS.register(new CommonProxy());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientProxy clientProxy = (ClientProxy) PROXY;
            NeoForge.EVENT_BUS.addListener(clientProxy::renderNametagEvent);
            NeoForge.EVENT_BUS.addListener(clientProxy::onAttackEntityFromClient);
        }

        PROXY.init();
    }

    private void setupClient(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PROXY.clientInit());
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> PROXY.serverInit());
    }
}

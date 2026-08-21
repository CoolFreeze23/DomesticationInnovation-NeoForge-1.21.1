package com.github.alexthe668.domesticationinnovation.server.block;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.UUID;

/**
 * Chunk loading ticket controller for pet recovery (Wayward Lantern).
 * Replaces Forge's ForgeChunkManager with NeoForge's TicketController API.
 * Tickets are keyed per pet UUID; any tickets left over from a previous run
 * (crash or shutdown mid-request) are discarded when the world loads.
 */
@EventBusSubscriber(modid = DomesticationMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DIChunkLoadingRegistry {

    public static final TicketController PET_TICKET_CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "pets"),
            DIChunkLoadingRegistry::removeAllChunkTickets);

    @SubscribeEvent
    public static void onRegisterTicketControllers(RegisterTicketControllersEvent event) {
        event.register(PET_TICKET_CONTROLLER);
    }

    private static void removeAllChunkTickets(ServerLevel serverLevel, TicketHelper ticketHelper) {
        int i = 0;
        for (UUID owner : ticketHelper.getEntityTickets().keySet()) {
            ticketHelper.removeAllTickets(owner);
            i++;
        }
        DomesticationMod.LOGGER.debug("Removed " + i + " chunkloading tickets");
    }
}

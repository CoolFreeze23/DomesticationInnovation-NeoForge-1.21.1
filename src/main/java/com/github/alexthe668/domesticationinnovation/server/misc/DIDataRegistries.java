package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.misc.data.TamingDefinition;
import com.github.alexthe668.domesticationinnovation.server.misc.data.TransformationDefinition;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * The two datapack dynamic registries behind data-driven taming and
 * transformations, plus the entity-type tags that gate them.
 *
 * Datapack locations:
 * <ul>
 *   <li>{@code data/<ns>/domesticationinnovation/taming/*.json} - {@link TamingDefinition}</li>
 *   <li>{@code data/<ns>/domesticationinnovation/transformation/*.json} - {@link TransformationDefinition}</li>
 * </ul>
 *
 * Both registries are SYNCED to clients (the entry codec doubles as the
 * network codec) so the interaction handler in CommonProxy can answer
 * "would this click do something" on the client too and cancel the client
 * event - suppressing vanilla interaction prediction and the offhand
 * fall-through - without a round trip. All side effects (consuming items,
 * taming, transforming) stay server-side in CommonProxy.
 */
public class DIDataRegistries {

    public static final ResourceKey<Registry<TamingDefinition>> TAMING =
            ResourceKey.createRegistryKey(id("taming"));
    public static final ResourceKey<Registry<TransformationDefinition>> TRANSFORMATION =
            ResourceKey.createRegistryKey(id("transformation"));

    /**
     * Hard veto consulted before any data-driven taming. Server owners and
     * pack makers drop problem mobs in here; nothing in code can override it.
     */
    public static final TagKey<EntityType<?>> TAMING_BLACKLIST =
            TagKey.create(Registries.ENTITY_TYPE, id("taming_blacklist"));

    /**
     * Mobs on this tag run the Brain AI system instead of goal selectors, so
     * generic goal surgery would be inert at best and corrupting at worst.
     * They get a movement restriction (restrictTo-based wander) and the
     * attachment-backed owner state, nothing else.
     */
    public static final TagKey<EntityType<?>> USES_BRAIN_AI =
            TagKey.create(Registries.ENTITY_TYPE, id("uses_brain_ai"));

    /** Registered on the mod event bus by DomesticationMod. */
    public static void onNewDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
        // Synced (third argument is the network codec): the definition codecs
        // are NbtOps-safe, so the same codec serves both disk and network.
        // Clients need these registries present so the interaction handler's
        // client-side match-and-cancel branches work.
        event.dataPackRegistry(TAMING, TamingDefinition.CODEC, TamingDefinition.CODEC);
        event.dataPackRegistry(TRANSFORMATION, TransformationDefinition.CODEC, TransformationDefinition.CODEC);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, path);
    }
}

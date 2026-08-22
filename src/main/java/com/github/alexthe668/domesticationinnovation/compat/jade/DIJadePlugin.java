package com.github.alexthe668.domesticationinnovation.compat.jade;

import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Optional Jade integration. Jade discovers this class through its
 * {@code @WailaPlugin} annotation scan, so it is only ever classloaded when
 * Jade is installed - nothing in the always-loaded code may reference this
 * package, or the mod would crash without Jade present.
 *
 * <p>Each provider registers under its own UID, so Jade's plugin settings
 * screen offers a separate toggle for the enchantment list and the pet bed
 * line.</p>
 */
@WailaPlugin
public class DIJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(JadePetEnchantmentsProvider.INSTANCE, LivingEntity.class);
        registration.registerEntityComponent(JadePetBedProvider.INSTANCE, LivingEntity.class);
    }
}

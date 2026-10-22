package com.rem.gaugeenhancement.mixin;

import com.rem.gaugeenhancement.inerface_injection.InjectItemStack;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * @author ZZZank
 */
@Mixin(ItemStack.class)
public abstract class MixinItemStack implements InjectItemStack {

    @Override
    public void gaugeEnhancement$doNothing() {
    }
}

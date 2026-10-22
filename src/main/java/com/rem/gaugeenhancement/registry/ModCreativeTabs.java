package com.rem.gaugeenhancement.registry;

import com.rem.gaugeenhancement.GaugeEnhancement;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GaugeEnhancement.MOD_ID);

	public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register(
			"main",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup." + GaugeEnhancement.MOD_ID))
					.icon(() -> new ItemStack(ModItems.DYNAMIC_COMPARATOR_GAUGE.get()))
					.displayItems((params, output) -> {
						output.accept(ModItems.DYNAMIC_COMPARATOR_GAUGE.get());
						output.accept(ModItems.GAUGE_ICON.get());
					})
					.build()
	);

	private ModCreativeTabs() {
	}

	public static void register(IEventBus modBus) {
		CREATIVE_MODE_TABS.register(modBus);
	}
}

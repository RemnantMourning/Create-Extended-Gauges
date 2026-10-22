package com.rem.gaugeenhancement.registry;

import com.rem.gaugeenhancement.GaugeEnhancement;
import com.rem.gaugeenhancement.content.logistics.board.DynamicComparatorPanelBehaviour;
import net.liukrast.eg.api.EGRegistries;
import net.liukrast.eg.api.registry.PanelType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModPanels {
	public static final DeferredRegister<PanelType<?>> PANELS =
			DeferredRegister.create(EGRegistries.PANEL_REGISTRY_KEY, GaugeEnhancement.MOD_ID);

	public static final RegistryObject<PanelType<DynamicComparatorPanelBehaviour>> DYNAMIC_COMPARATOR =
			PANELS.register("dynamic_comparator", () -> new PanelType<>(
					DynamicComparatorPanelBehaviour::new,
					DynamicComparatorPanelBehaviour.class
			));

	private ModPanels() {
	}

	public static void register(IEventBus modBus) {
		PANELS.register(modBus);
	}
}

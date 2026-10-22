package com.rem.gaugeenhancement.registry;

import com.rem.gaugeenhancement.GaugeEnhancement;
import net.liukrast.eg.api.logistics.board.PanelBlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
	public static final DeferredRegister<Item> ITEMS =
			DeferredRegister.create(ForgeRegistries.ITEMS, GaugeEnhancement.MOD_ID);

	public static final RegistryObject<Item> GAUGE_ICON = ITEMS.register(
			"gauge_icon",
			() -> new Item(new Item.Properties())
	);

	public static final RegistryObject<PanelBlockItem> DYNAMIC_COMPARATOR_GAUGE = ITEMS.register(
			"dynamic_comparator_gauge",
			() -> new PanelBlockItem(ModPanels.DYNAMIC_COMPARATOR::get, new Item.Properties())
	);

	private ModItems() {
	}

	public static void register(IEventBus modBus) {
		ITEMS.register(modBus);
	}
}

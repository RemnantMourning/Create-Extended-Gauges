package com.rem.gaugeenhancement;

import com.rem.gaugeenhancement.network.ModNetwork;
import com.rem.gaugeenhancement.registry.ModCreativeTabs;
import com.rem.gaugeenhancement.registry.ModItems;
import com.rem.gaugeenhancement.registry.ModPanels;
import com.rem.gaugeenhancement.registry.ModPartialModels;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(GaugeEnhancement.MOD_ID)
public class GaugeEnhancement {

	public static final String MOD_ID = "create_gauge_enhancement";
	public static final Logger LOGGER = LogManager.getLogger();

	public GaugeEnhancement(FMLJavaModLoadingContext context) {
		IEventBus modBus = context.getModEventBus();

		ModPanels.register(modBus);
		ModItems.register(modBus);
		ModCreativeTabs.register(modBus);
		ModPartialModels.init();
		modBus.addListener(this::commonSetup);

		LOGGER.info("{} initialized", MOD_ID);
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(ModNetwork::register);
	}
}

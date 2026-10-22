package com.rem.gaugeenhancement.registry;

import com.rem.gaugeenhancement.GaugeEnhancement;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public final class ModPartialModels {
	public static final PartialModel DYNAMIC_COMPARATOR_PANEL =
			PartialModel.of(ResourceLocation.fromNamespaceAndPath(GaugeEnhancement.MOD_ID, "block/dynamic_comparator_gauge"));

	private ModPartialModels() {
	}

	/** Forces class loading so PartialModels register on both sides. */
	public static void init() {
	}
}

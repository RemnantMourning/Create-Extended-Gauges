package com.rem.gaugeenhancement.mixin;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import net.liukrast.eg.api.logistics.board.AbstractPanelBehaviour;
import net.liukrast.eg.api.util.IFPExtra;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After connecting EG panels, invalidate cached wire paths on both ends so Create
 * recalculates routes (avoids leftover / wrong-colored segments).
 * <p>
 * Do not mutate {@code FACING} here — rotating floor gauges on connect causes
 * Dynamic Comparator ↔ Dynamic Comparator display glitches.
 */
@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public class FactoryPanelBehaviourMixin {

	@Inject(method = "addConnection", at = @At("TAIL"))
	private void gaugeEnhancement$clearPathsAfterConnect(FactoryPanelPosition fromPos, CallbackInfo ci) {
		FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
		if (!(self instanceof AbstractPanelBehaviour))
			return;

		Level level = self.getWorld();
		if (level == null)
			return;

		clearPaths(self);
		markRedraw(self);

		FactoryPanelBehaviour source = FactoryPanelBehaviour.at(level, fromPos);
		if (source != null) {
			clearPaths(source);
			markRedraw(source);
		}
	}

	private static void clearPaths(FactoryPanelBehaviour panel) {
		for (FactoryPanelConnection connection : panel.targetedBy.values())
			connection.path.clear();
		for (FactoryPanelConnection connection : panel.targetedByLinks.values())
			connection.path.clear();
		if (panel instanceof IFPExtra extra) {
			for (FactoryPanelConnection connection : extra.extra_gauges$getExtra().values())
				connection.path.clear();
		}
	}

	private static void markRedraw(FactoryPanelBehaviour panel) {
		if (panel.blockEntity instanceof FactoryPanelBlockEntity fpbe) {
			fpbe.redraw = true;
			fpbe.lastShape = null;
		}
	}
}

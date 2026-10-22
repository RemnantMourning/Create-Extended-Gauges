package com.rem.gaugeenhancement.mixin;

import com.rem.gaugeenhancement.connection.IConnectionOrder;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import net.liukrast.eg.api.logistics.board.AbstractPanelBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.Map;

/**
 * Make EG consume helpers iterate connections in our stored merge/display order.
 */
@Mixin(value = AbstractPanelBehaviour.class, remap = false)
public class AbstractPanelBehaviourConsumeMixin {

	@Redirect(
			method = "consumeForPanels",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;", ordinal = 0))
	private Collection<FactoryPanelConnection> gaugeEnhancement$sortedPanels(Map<?, ?> map) {
		return sorted(map, IConnectionOrder.ConnKind.PANEL);
	}

	@Redirect(
			method = "consumeForLinks",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;", ordinal = 0))
	private Collection<FactoryPanelConnection> gaugeEnhancement$sortedLinks(Map<?, ?> map) {
		return sorted(map, IConnectionOrder.ConnKind.LINK);
	}

	@Redirect(
			method = "consumeForExtra",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;", ordinal = 0))
	private Collection<FactoryPanelConnection> gaugeEnhancement$sortedExtras(Map<?, ?> map) {
		return sorted(map, IConnectionOrder.ConnKind.EXTRA);
	}

	@SuppressWarnings("unchecked")
	private Collection<FactoryPanelConnection> sorted(Map<?, ?> map, IConnectionOrder.ConnKind kind) {
		Collection<FactoryPanelConnection> values = (Collection<FactoryPanelConnection>) map.values();
		FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
		if (self instanceof IConnectionOrder order)
			return order.gaugeEnhancement$sorted(values, kind);
		return values;
	}
}

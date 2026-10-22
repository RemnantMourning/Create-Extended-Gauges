package com.rem.gaugeenhancement.mixin;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConfigurationPacket;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import net.liukrast.eg.api.util.IFPExtra;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create only removes {@code targetedBy}. Also remove EG extras and single support links.
 */
@Mixin(value = FactoryPanelConfigurationPacket.class, remap = false)
public class FactoryPanelConfigurationPacketMixin {

	@Shadow
	private PanelSlot slot;

	@Shadow
	private FactoryPanelPosition removeConnection;

	@Inject(method = "applySettings(Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBlockEntity;)V", at = @At("TAIL"))
	private void gaugeEnhancement$removeExtrasAndLinks(FactoryPanelBlockEntity be, CallbackInfo ci) {
		if (removeConnection == null)
			return;
		FactoryPanelBehaviour behaviour = be.panels.get(slot);
		if (behaviour == null)
			return;

		if (behaviour instanceof IFPExtra extra)
			extra.extra_gauges$getExtra().remove(removeConnection.pos());

		FactoryPanelConnection linkConn = behaviour.targetedByLinks.remove(removeConnection.pos());
		if (linkConn != null) {
			FactoryPanelSupportBehaviour link = FactoryPanelBehaviour.linkAt(be.getLevel(), linkConn);
			if (link != null)
				link.disconnect(behaviour);
			be.notifyUpdate();
		}
	}
}

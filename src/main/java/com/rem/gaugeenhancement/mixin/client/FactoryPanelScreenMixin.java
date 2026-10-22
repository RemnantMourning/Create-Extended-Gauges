package com.rem.gaugeenhancement.mixin.client;

import com.rem.gaugeenhancement.client.NetworkTopologyWidget;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FactoryPanelScreen.class, remap = false)
public abstract class FactoryPanelScreenMixin extends AbstractSimiScreen {

	@Shadow
	private FactoryPanelBehaviour behaviour;

	@Unique
	private NetworkTopologyWidget gaugeEnhancement$topology;

	@Inject(method = "init", at = @At("TAIL"))
	private void gaugeEnhancement$addTopology(CallbackInfo ci) {
		gaugeEnhancement$topology = new NetworkTopologyWidget(0, 0, behaviour);
		gaugeEnhancement$topology.relayout(width, height, guiLeft, guiTop, windowWidth);
		addRenderableWidget(gaugeEnhancement$topology);
	}

	@Inject(method = "renderWindow", at = @At("HEAD"))
	private void gaugeEnhancement$syncTopologyPos(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		if (gaugeEnhancement$topology != null)
			gaugeEnhancement$topology.relayout(width, height, guiLeft, guiTop, windowWidth);
	}
}

package com.rem.gaugeenhancement.mixin.client;

import com.rem.gaugeenhancement.client.NetworkTopologyWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.liukrast.eg.api.logistics.board.AbstractPanelBehaviour;
import net.liukrast.eg.api.logistics.board.BasicPanelScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BasicPanelScreen.class, remap = false)
public abstract class BasicPanelScreenMixin extends AbstractSimiScreen {

	@Shadow
	@Final
	public AbstractPanelBehaviour behaviour;

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

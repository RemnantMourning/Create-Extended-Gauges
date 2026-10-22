package com.rem.gaugeenhancement.client;

import com.rem.gaugeenhancement.GaugeEnhancement;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Fullscreen topology editor — same connect / reorder behaviour as the mini preview.
 */
public class NetworkTopologyScreen extends Screen {

	private final Screen parent;
	private final NetworkTopologyPanel panel;

	public NetworkTopologyScreen(Screen parent, FactoryPanelBehaviour behaviour) {
		super(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.fullscreen"));
		this.parent = parent;
		this.panel = new NetworkTopologyPanel(behaviour);
	}

	@Override
	protected void init() {
		super.init();
		panel.setFullscreen(true, width - 20, height - 20);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		panel.setFullscreen(true, width - 20, height - 20);
		panel.render(graphics, font, 10, 10, mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawString(font,
				Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_close"),
				14, height - 14, 0x888888, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (panel.mouseClicked(mouseX, mouseY, button))
			return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (panel.mouseDragged(mouseX, mouseY, button, dragX, dragY))
			return true;
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (panel.isInteracting() && panel.mouseReleased(mouseX, mouseY, button))
			return true;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (panel.mouseScrolled(mouseX, mouseY, delta))
			return true;
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

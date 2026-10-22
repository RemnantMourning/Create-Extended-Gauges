package com.rem.gaugeenhancement.client;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Mini topology preview beside the gauge screen. Long-press title opens fullscreen.
 */
public class NetworkTopologyWidget extends AbstractWidget {

	private static final int GAP = 8;

	private final FactoryPanelBehaviour behaviour;
	private final NetworkTopologyPanel panel;

	public NetworkTopologyWidget(int x, int y, FactoryPanelBehaviour behaviour) {
		super(x, y, NetworkTopologyPanel.PANEL_WIDTH, NetworkTopologyPanel.PANEL_HEIGHT, Component.empty());
		this.behaviour = behaviour;
		this.panel = new NetworkTopologyPanel(behaviour);
		this.panel.setFullscreen(false, NetworkTopologyPanel.PANEL_WIDTH, NetworkTopologyPanel.PANEL_HEIGHT);
		this.panel.setOpenFullscreen(this::openFullscreen);
	}

	private void openFullscreen() {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(new NetworkTopologyScreen(mc.screen, behaviour));
	}

	public void moveTo(int x, int y) {
		setX(x);
		setY(y);
	}

	public void relayout(int screenW, int screenH, int guiLeft, int guiTop, int windowW) {
		int pw = NetworkTopologyPanel.PANEL_WIDTH;
		int ph = NetworkTopologyPanel.PANEL_HEIGHT;
		int x;
		int y;
		if (guiLeft >= pw + GAP + 4) {
			x = guiLeft - pw - GAP;
			y = guiTop;
		} else if (guiTop >= ph + GAP + 4) {
			x = guiLeft;
			y = guiTop - ph - GAP;
		} else if (guiLeft + windowW + GAP + pw <= screenW - 4) {
			x = guiLeft + windowW + GAP;
			y = guiTop;
		} else {
			x = 4;
			y = 4;
		}
		x = Mth.clamp(x, 4, Math.max(4, screenW - pw - 4));
		y = Mth.clamp(y, 4, Math.max(4, screenH - ph - 4));
		moveTo(x, y);
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		panel.setFullscreen(false, NetworkTopologyPanel.PANEL_WIDTH, NetworkTopologyPanel.PANEL_HEIGHT);
		panel.render(graphics, Minecraft.getInstance().font, getX(), getY(), mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!this.active || !this.visible || !isMouseOver(mouseX, mouseY))
			return false;
		return panel.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		return panel.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (panel.isInteracting())
			return panel.mouseReleased(mouseX, mouseY, button);
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (panel.mouseScrolled(mouseX, mouseY, delta))
			return true;
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}

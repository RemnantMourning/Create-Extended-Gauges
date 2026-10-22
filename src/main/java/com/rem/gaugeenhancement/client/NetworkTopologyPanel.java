package com.rem.gaugeenhancement.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rem.gaugeenhancement.GaugeEnhancement;
import com.rem.gaugeenhancement.connection.IConnectionOrder;
import com.rem.gaugeenhancement.network.ConnectionOrderPacket;
import com.rem.gaugeenhancement.network.ModNetwork;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConfigurationPacket;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionPacket;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkScreen;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.outliner.Outliner;
import net.liukrast.eg.api.logistics.board.AbstractPanelBehaviour;
import net.liukrast.eg.api.logistics.board.PanelConnection;
import net.liukrast.eg.api.util.IFPExtra;
import net.liukrast.eg.registry.EGPanelConnections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interactive network topology. Mini preview auto-scales; long-press title opens fullscreen.
 */
public class NetworkTopologyPanel {

	public static final int PANEL_WIDTH = 186;
	public static final int PANEL_HEIGHT = 186;
	private static final int NODE = 30;
	private static final int PORT = 10;
	private static final int TITLE_H = 18;
	private static final long LONG_PRESS_MS = 450;

	private static final int BG = 0xE0101018;
	private static final int BORDER = 0xFF6A6D79;
	private static final int TITLE = 0xE8E0D0;
	private static final int WIRE_IN = 0xFF5BA3E0;
	private static final int WIRE_OUT = 0xFFE0A050;
	private static final int WIRE_LINK = 0xFFB070E0;
	private static final int WIRE_EXTRA = 0xFF70C070;
	private static final int NODE_SELF = 0xFF3A8A7A;
	private static final int NODE_IN = 0xFF3A5A8A;
	private static final int NODE_OUT = 0xFF8A6A3A;
	private static final int NODE_LINK = 0xFF6A3A8A;
	private static final int NODE_EXTRA = 0xFF3A7A4A;
	private static final int PORT_BG = 0xFF1E1E28;
	private static final int PORT_HL = 0xFFE8C060;
	private static final int PORT_EDGE = 0xFFD0D0D8;
	private static final int NODE_CANDIDATE = 0xFF555566;
	private static final int SNAP_PX = 32;
	private static final int SCAN_RANGE = 16;

	private final FactoryPanelBehaviour behaviour;
	private final Map<String, float[]> layout = new HashMap<>();
	private final List<TopoNode> nodes = new ArrayList<>();
	private final List<TopoWire> wires = new ArrayList<>();

	private boolean fullscreen;
	private int canvasW = PANEL_WIDTH;
	private int canvasH = PANEL_HEIGHT;
	private float contentScale = 1f;
	private Runnable openFullscreen;

	private int originX;
	private int originY;
	private String dragging;
	private double dragOx;
	private double dragOy;
	private double pressX;
	private double pressY;
	private boolean didDrag;
	private long chromePressAt;
	private double chromePressX;
	private double chromePressY;
	private List<Component> tooltip = List.of();
	private TopoWire hoverWire;
	private WireDrag wireDrag;
	private String snapKey;

	public NetworkTopologyPanel(FactoryPanelBehaviour behaviour) {
		this.behaviour = behaviour;
	}

	public void setFullscreen(boolean fullscreen, int width, int height) {
		this.fullscreen = fullscreen;
		this.canvasW = fullscreen ? Math.max(PANEL_WIDTH, width) : PANEL_WIDTH;
		this.canvasH = fullscreen ? Math.max(PANEL_HEIGHT, height) : PANEL_HEIGHT;
		this.contentScale = 1f;
	}

	public void setOpenFullscreen(Runnable openFullscreen) {
		this.openFullscreen = openFullscreen;
	}

	public int canvasWidth() {
		return canvasW;
	}

	public int canvasHeight() {
		return canvasH;
	}

	public void render(GuiGraphics g, Font font, int x, int y, int mouseX, int mouseY) {
		originX = x;
		originY = y;
		updateContentScale();
		rebuildGraph();
		tickLongPress();

		g.fill(originX - 1, originY - 1, originX + canvasW + 1, originY + canvasH + 1, BORDER);
		g.fill(originX, originY, originX + canvasW, originY + canvasH, BG);
		g.drawString(font, Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology"),
				originX + 6, originY + 5, TITLE, false);
		if (!fullscreen) {
			Component hint = Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_fullscreen");
			g.drawString(font, hint, originX + canvasW - font.width(hint) - 6, originY + 5, 0xFFAA88, false);
		}

		assignOrderIndices();

		g.enableScissor(originX + 1, originY + TITLE_H, originX + canvasW - 1, originY + canvasH - 1);
		hoverWire = null;
		g.pose().pushPose();
		g.pose().translate(originX, originY + TITLE_H, 0);
		g.pose().scale(contentScale, contentScale, 1f);
		g.pose().translate(-originX, -(originY + TITLE_H), 0);
		int lmx = logicalX(mouseX);
		int lmy = logicalY(mouseY);
		for (TopoWire wire : wires) {
			if (visibleInConnectMode(wire))
				drawWire(g, font, wire, lmx, lmy);
		}
		tooltip = List.of();
		for (TopoNode node : nodes) {
			if (visibleInConnectMode(node))
				drawNode(g, font, node, lmx, lmy);
		}
		if (wireDrag != null)
			drawWireDrag(g, lmx, lmy);
		g.pose().popPose();
		g.disableScissor();

		updateWorldHighlights();

		if (wires.isEmpty() && wireDrag == null) {
			g.drawString(font, Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.empty"),
					originX + 6, originY + canvasH - 14, 0x888888, false);
		}
		if (chromePressAt > 0 && tooltip.isEmpty()) {
			tooltip = List.of(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_fullscreen")
					.withStyle(s -> s.withColor(0xFFE0A0)));
		} else if (wireDrag != null && tooltip.isEmpty()) {
			tooltip = List.of(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_wire")
					.withStyle(s -> s.withColor(0xFFE0A0)));
		} else if (hoverWire != null && hoverWire.orderable && hoverWire.orderIndex > 0 && tooltip.isEmpty()) {
			tooltip = List.of(
					Component.literal(hoverWire.type.shortLabel() + " " + hoverWire.orderIndex),
					Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_reorder")
							.withStyle(s -> s.withColor(0xFFE0A0)));
		}
		if (!tooltip.isEmpty())
			g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
	}

	private void updateContentScale() {
		if (fullscreen) {
			contentScale = 1f;
			return;
		}
		int left = behaviour.targetedBy.size() + behaviour.targetedByLinks.size();
		if (behaviour instanceof IFPExtra ifp)
			left += ifp.extra_gauges$getExtra().size();
		int rows = Math.max(1, Math.max(left, behaviour.targeting.size()));
		float avail = canvasH - TITLE_H - 8f;
		float need = 10 + rows * (NODE + 14);
		contentScale = Mth.clamp(avail / need, 0.42f, 1f);
	}

	private void tickLongPress() {
		if (fullscreen || chromePressAt <= 0 || openFullscreen == null)
			return;
		if (System.currentTimeMillis() - chromePressAt < LONG_PRESS_MS)
			return;
		if (Math.hypot(pressX - chromePressX, pressY - chromePressY) > 6)
			return;
		chromePressAt = 0;
		clickSound();
		openFullscreen.run();
	}

	private int logicalX(double mouseX) {
		return (int) Math.round(originX + (mouseX - originX) / contentScale);
	}

	private int logicalY(double mouseY) {
		float top = originY + TITLE_H;
		return (int) Math.round(top + (mouseY - top) / contentScale);
	}

	public boolean isInteracting() {
		return wireDrag != null || dragging != null || chromePressAt > 0;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY))
			return false;

		if (!fullscreen && button == 0 && mouseY >= originY && mouseY < originY + TITLE_H) {
			chromePressAt = System.currentTimeMillis();
			chromePressX = mouseX;
			chromePressY = mouseY;
			pressX = mouseX;
			pressY = mouseY;
			return true;
		}

		mouseX = logicalX(mouseX);
		mouseY = logicalY(mouseY);

		TopoWire wireHit = hitWireLabel(mouseX, mouseY);
		if (wireHit != null && wireHit.orderable) {
			if (button == 0) {
				nudgeOrder(wireHit, 1);
				clickSound();
				return true;
			}
			if (button == 1) {
				nudgeOrder(wireHit, -1);
				clickSound();
				return true;
			}
		}

		TopoHit hit = hitTest(mouseX, mouseY);
		if (hit == null)
			return true;

		if (button == 1) {
			if (hit.part == HitPart.BADGE && hit.node.kind.supportsPortCycle()) {
				cyclePort(hit.node);
				clickSound();
				return true;
			}
			if (hit.node.kind != Kind.SELF && hit.node.kind != Kind.CANDIDATE) {
				disconnect(hit.node);
				clickSound();
			}
			return true;
		}

		if (button == 0) {
			if (hit.part == HitPart.BADGE && hit.node.kind.supportsPortCycle()) {
				cyclePort(hit.node);
				clickSound();
				return true;
			}
			if (hit.part == HitPart.LEFT || hit.part == HitPart.RIGHT) {
				startWireDrag(hit.node, hit.part == HitPart.LEFT, mouseX, mouseY);
				return true;
			}
			dragging = hit.node.key;
			dragOx = mouseX - (originX + hit.node.x);
			dragOy = mouseY - (originY + hit.node.y);
			pressX = mouseX;
			pressY = mouseY;
			didDrag = false;
			return true;
		}
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (!contains(mouseX, mouseY))
			return false;
		mouseX = logicalX(mouseX);
		mouseY = logicalY(mouseY);
		TopoWire wireHit = hitWireLabel(mouseX, mouseY);
		if (wireHit == null || !wireHit.orderable)
			return false;
		nudgeOrder(wireHit, delta > 0 ? -1 : 1);
		clickSound();
		return true;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (chromePressAt > 0) {
			pressX = mouseX;
			pressY = mouseY;
			if (Math.hypot(mouseX - chromePressX, mouseY - chromePressY) > 6)
				chromePressAt = 0;
			return true;
		}
		mouseX = logicalX(mouseX);
		mouseY = logicalY(mouseY);
		if (wireDrag != null) {
			if (!didDrag && (Math.abs(mouseX - pressX) > 3 || Math.abs(mouseY - pressY) > 3))
				didDrag = true;
			wireDrag.curX = mouseX;
			wireDrag.curY = mouseY;
			snapKey = findSnapKey(mouseX, mouseY);
			return true;
		}
		if (dragging == null)
			return false;
		if (!didDrag && (Math.abs(mouseX - pressX) > 3 || Math.abs(mouseY - pressY) > 3))
			didDrag = true;
		if (!didDrag)
			return true;
		float nx = Mth.clamp((float) (mouseX - originX - dragOx), 8, canvasW - NODE - 8);
		float ny = Mth.clamp((float) (mouseY - originY - dragOy), TITLE_H + 4, canvasH - NODE - 8);
		layout.put(dragging, new float[]{nx, ny});
		return true;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (chromePressAt > 0) {
			chromePressAt = 0;
			return true;
		}
		mouseX = logicalX(mouseX);
		mouseY = logicalY(mouseY);
		if (wireDrag != null) {
			finishWireDrag(mouseX, mouseY);
			wireDrag = null;
			snapKey = null;
			didDrag = false;
			return true;
		}
		if (dragging == null)
			return false;
		String key = dragging;
		dragging = null;
		if (!didDrag && button == 0) {
			TopoNode clicked = findNode(key);
			if (clicked != null && clicked.kind != Kind.SELF && clicked.kind != Kind.CANDIDATE)
				openConfig(clicked);
		}
		didDrag = false;
		return true;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= originX && mouseX < originX + canvasW
				&& mouseY >= originY && mouseY < originY + canvasH;
	}

	private boolean isConnectMode() {
		return wireDrag != null && !wireDrag.pickupExisting;
	}

	private boolean visibleInConnectMode(TopoNode n) {
		if (!isConnectMode())
			return true;
		return n.kind == Kind.SELF || n.kind == Kind.CANDIDATE;
	}

	private boolean visibleInConnectMode(TopoWire w) {
		return !isConnectMode();
	}

	private void updateWorldHighlights() {
		if (!isConnectMode())
			return;
		boolean flashOn = ((System.currentTimeMillis() / 280) & 1) == 0;
		int flashColor = flashOn ? 0xFFE060 : 0x8A7020;
		for (TopoNode n : nodes) {
			if (n.kind != Kind.CANDIDATE)
				continue;
			BlockPos pos = n.linkPos != null ? n.linkPos
					: (n.panelPos != null ? n.panelPos.pos() : null);
			if (pos == null)
				continue;
			boolean snapped = snapKey != null && snapKey.equals(n.key);
			Outliner.getInstance()
					.showAABB("ge_topo_" + n.key, new AABB(pos))
					.colored(snapped ? 0x80FF80 : flashColor)
					.disableLineNormals()
					.lineWidth(1 / 16f);
		}
	}

	private void rebuildGraph() {
		nodes.clear();
		wires.clear();
		Level level = level();
		if (level == null || behaviour == null)
			return;

		FactoryPanelPosition selfPos = behaviour.getPanelPosition();
		ensureLayout("self", canvasW / 2f - NODE / 2f, canvasH / 2f - 4);
		float[] selfL = layout.get("self");
		PortType selfType = primaryPortType(behaviour);
		TopoNode self = new TopoNode("self", Kind.SELF, selfPos, null,
				iconFor(behaviour), displayName(behaviour),
				selfL[0], selfL[1], selfPos.slot(), selfType, true);
		nodes.add(self);

		int leftRow = 0;
		for (FactoryPanelConnection c : behaviour.targetedBy.values()) {
			String key = "in:" + posKey(c.from);
			ensureLayout(key, 14, TITLE_H + 10 + leftRow++ * (NODE + 14));
			FactoryPanelBehaviour src = FactoryPanelBehaviour.at(level, c.from);
			float[] loc = layout.get(key);
			PortType type = sharedPortType(src, behaviour);
			TopoNode n = new TopoNode(key, Kind.INPUT, c.from, null, iconFor(src),
					displayName(src), loc[0], loc[1], c.from.slot(), type, src != null);
			nodes.add(n);
			wires.add(new TopoWire(n, self, wireColor(type, WIRE_IN), type,
					IConnectionOrder.keyPanel(c.from), true, 0, 0, 0, 0, 0));
		}

		for (FactoryPanelConnection c : behaviour.targetedByLinks.values()) {
			String key = "link:" + c.from.pos().asLong();
			ensureLayout(key, 14, TITLE_H + 10 + leftRow++ * (NODE + 14));
			float[] loc = layout.get(key);
			BlockEntity be = level.getBlockEntity(c.from.pos());
			boolean openable = be instanceof DisplayLinkBlockEntity;
			PortType type = PortType.LINK;
			// Display collectors feed STRING into string gauges
			if (be instanceof DisplayLinkBlockEntity)
				type = PortType.STRING;
			TopoNode n = new TopoNode(key, Kind.LINK, c.from, c.from.pos(), linkIcon(level, c),
					linkName(level, c), loc[0], loc[1], c.from.slot(), type, openable);
			nodes.add(n);
			wires.add(new TopoWire(n, self, wireColor(type, WIRE_LINK), type,
					IConnectionOrder.keyLink(c.from.pos()), true, 0, 0, 0, 0, 0));
		}

		if (behaviour instanceof IFPExtra ifp) {
			for (FactoryPanelConnection c : ifp.extra_gauges$getExtra().values()) {
				BlockPos pos = c.from.pos();
				String key = "extra:" + pos.asLong();
				ensureLayout(key, 14, TITLE_H + 10 + leftRow++ * (NODE + 14));
				float[] loc = layout.get(key);
				BlockState state = level.getBlockState(pos);
				PortType type = extraPortType(state.getBlock());
				boolean openable = level.getBlockEntity(pos) instanceof SignBlockEntity
						|| level.getBlockEntity(pos) instanceof DisplayLinkBlockEntity;
				TopoNode n = new TopoNode(key, Kind.EXTRA, c.from, pos,
						new ItemStack(state.getBlock().asItem()), state.getBlock().getName(),
						loc[0], loc[1], c.from.slot(), type, openable);
				nodes.add(n);
				wires.add(new TopoWire(n, self, wireColor(type, WIRE_EXTRA), type,
						IConnectionOrder.keyExtra(pos), true, 0, 0, 0, 0, 0));
			}
		}

		int rightRow = 0;
		for (FactoryPanelPosition t : behaviour.targeting) {
			String key = "out:" + posKey(t);
			ensureLayout(key, canvasW - NODE - 14, TITLE_H + 10 + rightRow++ * (NODE + 14));
			FactoryPanelBehaviour dest = FactoryPanelBehaviour.at(level, t);
			float[] loc = layout.get(key);
			PortType type = sharedPortType(behaviour, dest);
			TopoNode n = new TopoNode(key, Kind.OUTPUT, t, null, iconFor(dest),
					displayName(dest), loc[0], loc[1], t.slot(), type, dest != null);
			nodes.add(n);
			wires.add(new TopoWire(self, n, wireColor(type, WIRE_OUT), type,
					null, false, 0, 0, 0, 0, 0));
		}

		if (wireDrag != null)
			addNearbyCandidates(level, self, leftRow, rightRow);
	}

	private void addNearbyCandidates(Level level, TopoNode self, int leftRow, int rightRow) {
		FactoryPanelPosition selfPos = behaviour.getPanelPosition();
		BlockPos origin = selfPos.pos();
		boolean wantSources = wireDrag.fromLeft;
		if (wireDrag.pickupExisting)
			return;

		BlockState selfState = behaviour.blockEntity.getBlockState();
		if (!selfState.hasProperty(FactoryPanelBlock.FACE))
			return;
		Direction normal = FactoryPanelBlock.connectedDirection(selfState);
		AttachFace selfFace = selfState.getValue(FactoryPanelBlock.FACE);

		layout.keySet().removeIf(k -> k.startsWith("cand:") || k.startsWith("candlink:")
				|| k.startsWith("candextra:"));

		List<TopoNode> found = new ArrayList<>();
		for (BlockPos pos : planePositions(origin, normal, SCAN_RANGE)) {
			if (!level.isLoaded(pos))
				continue;
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof FactoryPanelBlockEntity fpbe) {
				BlockState otherBlock = fpbe.getBlockState();
				if (!otherBlock.hasProperty(FactoryPanelBlock.FACE)
						|| otherBlock.getValue(FactoryPanelBlock.FACE) != selfFace)
					continue;
				for (PanelSlot slot : PanelSlot.values()) {
					FactoryPanelBehaviour other = fpbe.panels.get(slot);
					if (other == null || !other.isActive())
						continue;
					FactoryPanelPosition otherPos = other.getPanelPosition();
					if (otherPos.equals(selfPos))
						continue;
					if (behaviour.targetedBy.containsKey(otherPos) || behaviour.targeting.contains(otherPos))
						continue;
					if (other.targetedBy.containsKey(selfPos) || other.targeting.contains(selfPos))
						continue;
					String key = "cand:" + posKey(otherPos);
					PortType type = sharedPortType(behaviour, other);
					found.add(new TopoNode(key, Kind.CANDIDATE, otherPos, null, iconFor(other),
							displayName(other), 0, 0, otherPos.slot(), type, false));
				}
			}
			if (!wantSources)
				continue;
			if (behaviour.targetedByLinks.containsKey(pos))
				continue;
			if (behaviour instanceof IFPExtra ifp && ifp.extra_gauges$getExtra().containsKey(pos))
				continue;

			if (be instanceof SmartBlockEntity sbe) {
				FactoryPanelSupportBehaviour link = sbe.getBehaviour(FactoryPanelSupportBehaviour.TYPE);
				if (link != null) {
					String key = "candlink:" + pos.asLong();
					FactoryPanelPosition fake = new FactoryPanelPosition(pos, PanelSlot.TOP_LEFT);
					boolean openable = be instanceof DisplayLinkBlockEntity;
					PortType type = be instanceof DisplayLinkBlockEntity ? PortType.STRING : PortType.LINK;
					found.add(new TopoNode(key, Kind.CANDIDATE, fake, pos,
							new ItemStack(be.getBlockState().getBlock().asItem()),
							be.getBlockState().getBlock().getName(),
							0, 0, PanelSlot.TOP_LEFT, type, openable));
					continue;
				}
			}

			BlockState state = level.getBlockState(pos);
			PortType extraType = extraPortType(state.getBlock());
			if (extraType == PortType.UNKNOWN)
				continue;
			String key = "candextra:" + pos.asLong();
			found.add(new TopoNode(key, Kind.CANDIDATE,
					new FactoryPanelPosition(pos, PanelSlot.TOP_LEFT), pos,
					new ItemStack(state.getBlock().asItem()), state.getBlock().getName(),
					0, 0, PanelSlot.TOP_LEFT, extraType, level.getBlockEntity(pos) instanceof SignBlockEntity));
		}

		float colX = wantSources ? 14f : canvasW - NODE - 14f;
		float y = TITLE_H + 8;
		for (TopoNode n : nodes) {
			if (Math.abs(n.x - colX) < 2f)
				y = Math.max(y, n.y + NODE + 12);
		}
		for (TopoNode n : found) {
			float yy = Math.min(y, canvasH - NODE - 4);
			layout.put(n.key, new float[]{colX, yy});
			nodes.add(new TopoNode(n.key, n.kind, n.panelPos, n.linkPos, n.icon, n.label,
					colX, yy, n.slot, n.portType, n.openable));
			y += NODE + 12;
		}
	}

	/** Positions on the board plane (perpendicular to the panel normal). */
	private static Iterable<BlockPos> planePositions(BlockPos origin, Direction normal, int range) {
		List<BlockPos> list = new ArrayList<>((range * 2 + 1) * (range * 2 + 1));
		for (int a = -range; a <= range; a++) {
			for (int b = -range; b <= range; b++) {
				int x = origin.getX(), y = origin.getY(), z = origin.getZ();
				switch (normal.getAxis()) {
					case X -> {
						y = origin.getY() + a;
						z = origin.getZ() + b;
					}
					case Y -> {
						x = origin.getX() + a;
						z = origin.getZ() + b;
					}
					case Z -> {
						x = origin.getX() + a;
						y = origin.getY() + b;
					}
				}
				list.add(new BlockPos(x, y, z));
			}
		}
		return list;
	}

	private void startWireDrag(TopoNode node, boolean left, double mouseX, double mouseY) {
		boolean pickup = node.kind == Kind.INPUT || node.kind == Kind.OUTPUT
				|| node.kind == Kind.LINK || node.kind == Kind.EXTRA;
		// Self left = seek inputs; self right = seek outputs.
		// Remote nodes: only their connecting side starts a pickup drag.
		if (node.kind == Kind.SELF) {
			// ok either side
		} else if (node.kind == Kind.INPUT || node.kind == Kind.LINK || node.kind == Kind.EXTRA) {
			if (left)
				return; // inputs connect via their right port
		} else if (node.kind == Kind.OUTPUT) {
			if (!left)
				return; // outputs connect via their left port
		} else if (node.kind == Kind.CANDIDATE) {
			return;
		}
		wireDrag = new WireDrag(node.key, left, pickup, mouseX, mouseY);
		snapKey = null;
		pressX = mouseX;
		pressY = mouseY;
		didDrag = false;
		clickSound();
	}

	private void finishWireDrag(double mouseX, double mouseY) {
		if (!didDrag)
			return; // click without drag — do not connect/disconnect
		snapKey = findSnapKey(mouseX, mouseY);
		TopoNode snap = snapKey != null ? findNode(snapKey) : null;

		if (wireDrag.pickupExisting) {
			// Detached from self → disconnect; snapped back to self → keep
			if (snap == null || snap.kind != Kind.SELF) {
				TopoNode remote = findNode(wireDrag.fromKey);
				if (remote != null && remote.kind != Kind.SELF && remote.kind != Kind.CANDIDATE) {
					disconnect(remote);
					clickSound();
				}
			}
			return;
		}

		TopoNode from = findNode(wireDrag.fromKey);
		if (snap == null || from == null || from.kind != Kind.SELF || snap.kind == Kind.SELF)
			return;

		transferCandidateLayout(snap);

		if (wireDrag.fromLeft) {
			FactoryPanelPosition fromPos = snap.linkPos != null
					? new FactoryPanelPosition(snap.linkPos, PanelSlot.TOP_LEFT)
					: snap.panelPos;
			if (fromPos == null)
				return;
			sendConnect(fromPos, behaviour.getPanelPosition());
		} else {
			if (snap.panelPos == null || snap.linkPos != null)
				return;
			sendConnect(behaviour.getPanelPosition(), snap.panelPos);
		}
		clickSound();
	}

	private void transferCandidateLayout(TopoNode snap) {
		float[] loc = layout.get(snap.key);
		if (loc == null)
			return;
		String realKey = null;
		if (snap.key.startsWith("cand:") && snap.panelPos != null)
			realKey = wireDrag != null && !wireDrag.fromLeft
					? "out:" + posKey(snap.panelPos)
					: "in:" + posKey(snap.panelPos);
		else if (snap.key.startsWith("candlink:") && snap.linkPos != null)
			realKey = "link:" + snap.linkPos.asLong();
		else if (snap.key.startsWith("candextra:") && snap.linkPos != null)
			realKey = "extra:" + snap.linkPos.asLong();
		if (realKey != null)
			layout.put(realKey, new float[]{loc[0], loc[1]});
	}

	private void sendConnect(FactoryPanelPosition from, FactoryPanelPosition to) {
		AllPackets.getChannel().sendToServer(new FactoryPanelConnectionPacket(from, to, false));
	}

	private String findSnapKey(double mouseX, double mouseY) {
		String best = null;
		double bestDist = SNAP_PX;
		for (TopoNode n : nodes) {
			if (!visibleInConnectMode(n))
				continue;
			if (wireDrag != null && n.key.equals(wireDrag.fromKey))
				continue;
			if (wireDrag != null && wireDrag.pickupExisting && n.kind != Kind.SELF)
				continue;
			if (isConnectMode()) {
				if (n.kind != Kind.CANDIDATE)
					continue;
			}
			int cx = originX + Math.round(n.x) + NODE / 2;
			int cy = originY + Math.round(n.y) + NODE / 2;
			double d = Math.hypot(mouseX - cx, mouseY - cy);
			if (d < bestDist) {
				bestDist = d;
				best = n.key;
			}
		}
		return best;
	}

	private void drawWireDrag(GuiGraphics g, int mouseX, int mouseY) {
		TopoNode from = findNode(wireDrag.fromKey);
		if (from == null)
			return;
		float x0 = originX + from.x + (wireDrag.fromLeft ? -PORT / 2f : NODE + PORT / 2f);
		float y0 = originY + from.y + NODE / 2f;
		TopoNode snap = snapKey != null ? findNode(snapKey) : null;
		float x1 = snap != null ? originX + snap.x - PORT + 1 + PORT / 2f : (float) wireDrag.curX;
		float y1 = snap != null ? originY + snap.y + NODE / 2f : (float) wireDrag.curY;
		int color = snap != null ? 0xFF80FF80 : 0xFFC0C0C0;
		float mid = snap != null ? (x0 + x1) * 0.5f : x0 + Math.max(12, (x1 - x0) * 0.5f);
		drawOrthoWire(g, x0, y0, mid, x1, y1, color);
		if (snap != null) {
			int sx = originX + Math.round(snap.x) - 2;
			int sy = originY + Math.round(snap.y) - 2;
			g.fill(sx, sy, sx + NODE + 4, sy + 2, 0xFF80FF80);
			g.fill(sx, sy + NODE + 2, sx + NODE + 4, sy + NODE + 4, 0xFF80FF80);
			g.fill(sx, sy, sx + 2, sy + NODE + 4, 0xFF80FF80);
			g.fill(sx + NODE + 2, sy, sx + NODE + 4, sy + NODE + 4, 0xFF80FF80);
		}
	}

	private void assignOrderIndices() {
		if (!(behaviour instanceof IConnectionOrder order))
			return;
		Map<PortType, List<TopoWire>> byType = new HashMap<>();
		for (TopoWire w : wires) {
			if (!w.orderable || w.orderKey == null)
				continue;
			byType.computeIfAbsent(w.type, t -> new ArrayList<>()).add(w);
		}
		List<TopoWire> replaced = new ArrayList<>();
		for (TopoWire w : wires) {
			if (!w.orderable || w.orderKey == null) {
				replaced.add(w);
				continue;
			}
			List<TopoWire> group = byType.get(w.type);
			List<String> keys = group.stream().map(g -> g.orderKey).toList();
			order.gaugeEnhancement$ensureOrders(keys);
			List<String> sorted = new ArrayList<>(keys);
			sorted.sort(Comparator.comparingInt(order::gaugeEnhancement$getOrder));
			int index = sorted.indexOf(w.orderKey) + 1;
			replaced.add(new TopoWire(w.from, w.to, w.color, w.type, w.orderKey, true, index,
					w.labelX, w.labelY, w.labelW, w.labelH));
		}
		wires.clear();
		wires.addAll(replaced);
	}

	private void nudgeOrder(TopoWire wire, int delta) {
		if (!(behaviour instanceof IConnectionOrder order) || !wire.orderable || wire.orderKey == null)
			return;
		List<String> groupKeys = new ArrayList<>();
		for (TopoWire w : wires) {
			if (w.orderable && w.type == wire.type && w.orderKey != null)
				groupKeys.add(w.orderKey);
		}
		order.gaugeEnhancement$ensureOrders(groupKeys);
		int cur = order.gaugeEnhancement$getOrder(wire.orderKey);
		int next = Mth.clamp(cur + delta, 1, groupKeys.size());
		if (next == cur)
			return;
		// Optimistic client update
		order.gaugeEnhancement$moveToIndex(wire.orderKey, next, groupKeys);
		ModNetwork.CHANNEL.sendToServer(new ConnectionOrderPacket(
				behaviour.getPanelPosition(), wire.orderKey, next, groupKeys));
	}

	private TopoWire hitWireLabel(double mouseX, double mouseY) {
		for (TopoWire w : wires) {
			if (!visibleInConnectMode(w))
				continue;
			if (!w.orderable || w.labelW <= 0)
				continue;
			if (mouseX >= w.labelX && mouseX < w.labelX + w.labelW
					&& mouseY >= w.labelY && mouseY < w.labelY + w.labelH)
				return w;
		}
		return null;
	}

	private void drawWire(GuiGraphics g, Font font, TopoWire wire, int mouseX, int mouseY) {
		float x0 = originX + wire.from.x + NODE + PORT / 2f;
		float y0 = originY + wire.from.y + NODE / 2f;
		float x1 = originX + wire.to.x - PORT + 1 + PORT / 2f;
		int fanIdx = 0;
		int fanCount = 0;
		for (TopoWire w : wires) {
			if (w.to.key.equals(wire.to.key)) {
				if (w.from.y < wire.from.y || (w.from.y == wire.from.y && w.from.key.compareTo(wire.from.key) < 0))
					fanIdx++;
				fanCount++;
			}
		}
		float y1 = originY + wire.to.y + NODE / 2f + fanOffset(fanIdx, fanCount);
		float gap = Math.max(24f, x1 - x0 - 20f);
		float channel = gap / (fanCount + 1);
		float midX = Mth.clamp(x0 + channel * (fanIdx + 1), x0 + 8, x1 - 14);
		drawOrthoWire(g, x0, y0, midX, x1, y1, wire.color);

		if (!wire.orderable || wire.orderIndex <= 0)
			return;
		String tag = wire.type.shortLabel() + " " + wire.orderIndex;
		int tw = font.width(tag);
		int mx = Math.round(midX) - tw / 2;
		int my = Math.round(y0) - 5;
		mx = Mth.clamp(mx, originX + 2, originX + canvasW - tw - 2);
		my = Mth.clamp(my, originY + TITLE_H + 1, originY + canvasH - 12);
		boolean hover = mouseX >= mx - 2 && mouseX < mx + tw + 2 && mouseY >= my - 1 && mouseY < my + 9;
		if (hover)
			hoverWire = wire;
		g.fill(mx - 2, my - 1, mx + tw + 2, my + 9, hover ? 0xE0303038 : 0xC0101018);
		g.drawString(font, tag, mx, my, wire.color, false);
		int idx = wires.indexOf(wire);
		if (idx >= 0)
			wires.set(idx, new TopoWire(wire.from, wire.to, wire.color, wire.type, wire.orderKey,
					wire.orderable, wire.orderIndex, mx - 2, my - 1, tw + 4, 10));
	}

	private static float fanOffset(int index, int count) {
		if (count <= 1)
			return 0f;
		float spacing = Math.min(9f, (NODE - 6f) / Math.max(1, count - 1));
		return (index - (count - 1) / 2f) * spacing;
	}

	/** Orthogonal route: right -> vertical channel -> into port. */
	private void drawOrthoWire(GuiGraphics g, float x0, float y0, float midX, float x1, float y1, int color) {
		drawThickLine(g, Math.round(x0), Math.round(y0), Math.round(midX), Math.round(y0), color);
		drawThickLine(g, Math.round(midX), Math.round(y0), Math.round(midX), Math.round(y1), color);
		drawThickLine(g, Math.round(midX), Math.round(y1), Math.round(x1), Math.round(y1), color);
		int ax = Math.round(x1);
		int ay = Math.round(y1);
		g.fill(ax - 3, ay - 2, ax + 1, ay + 2, color);
	}

	private void drawThickLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
		for (int i = 0; i <= steps; i++) {
			int x = x0 + (x1 - x0) * i / steps;
			int y = y0 + (y1 - y0) * i / steps;
			g.fill(x, y, x + 2, y + 2, color);
		}
	}

	private void drawNode(GuiGraphics g, Font font, TopoNode node, int mouseX, int mouseY) {
		int x = originX + Math.round(node.x);
		int y = originY + Math.round(node.y);
		int border = switch (node.kind) {
			case SELF -> NODE_SELF;
			case INPUT -> NODE_IN;
			case OUTPUT -> NODE_OUT;
			case LINK -> NODE_LINK;
			case EXTRA -> NODE_EXTRA;
			case CANDIDATE -> NODE_CANDIDATE;
		};
		boolean connectCand = isConnectMode() && node.kind == Kind.CANDIDATE;
		boolean flashOn = connectCand && ((System.currentTimeMillis() / 280) & 1) == 0;
		if (connectCand)
			border = flashOn ? 0xFFFFE060 : 0xFF8A7020;
		else if (snapKey != null && snapKey.equals(node.key))
			border = 0xFF80FF80;
		boolean hovered = mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE;
		int pad = connectCand ? 2 : 1;
		g.fill(x - pad, y - pad, x + NODE + pad, y + NODE + pad, border);
		int body = hovered ? 0xFF2A2A38 : 0xFF1A1A24;
		if (flashOn)
			body = 0xFF3A3420;
		g.fill(x, y, x + NODE, y + NODE, body);

		RenderSystem.enableBlend();
		g.renderItem(node.icon, x + 7, y + 7);
		g.renderItemDecorations(font, node.icon, x + 7, y + 7);

		boolean showLeft = showsLeftPort(node.kind);
		boolean showRight = showsRightPort(node.kind);
		boolean leftHover = false;
		boolean rightHover = false;
		if (showLeft) {
			int lx = x - PORT + 1;
			int ly = y + (NODE - PORT) / 2;
			leftHover = mouseX >= lx && mouseX < lx + PORT && mouseY >= ly && mouseY < ly + PORT;
			drawPortSocket(g, font, lx, ly, leftHover, node, true);
		}
		if (showRight) {
			int rx = x + NODE - 1;
			int ry = y + (NODE - PORT) / 2;
			rightHover = mouseX >= rx && mouseX < rx + PORT && mouseY >= ry && mouseY < ry + PORT;
			drawPortSocket(g, font, rx, ry, rightHover, node, false);
		}

		// Slot badge only for real panel gauges (board quadrant 左上/右上/左下/右下).
		// Signs / support links have no board slot — do not show a misleading 1-4.
		boolean badgeHover = false;
		if (node.kind.hasBoardSlot()) {
			int bx = x + NODE - PORT - 1;
			int by = y + NODE - PORT - 1;
			badgeHover = mouseX >= bx && mouseX < bx + PORT && mouseY >= by && mouseY < by + PORT;
			g.fill(bx - 1, by - 1, bx + PORT + 1, by + PORT + 1, PORT_EDGE);
			g.fill(bx, by, bx + PORT, by + PORT, badgeHover ? PORT_HL : PORT_BG);
			g.drawString(font, slotLetter(node.slot), bx + 2, by + 1, 0xFFFFFF, false);
		}

		if (hovered || leftHover || rightHover || badgeHover) {
			List<Component> tip = new ArrayList<>();
			tip.add(node.label);
			if (node.kind.hasBoardSlot())
				tip.add(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.slot_line",
						slotName(node.slot)));
			tip.add(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.port_line",
					node.portType.fullLabel()));
			BlockPos p = node.panelPos != null ? node.panelPos.pos() : node.linkPos;
			if (p != null)
				tip.add(Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ())
						.withStyle(s -> s.withColor(0x888888)));
			if (node.kind == Kind.CANDIDATE) {
				tip.add(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_wire")
						.withStyle(s -> s.withColor(0xFFE0A0)));
			} else if (node.kind != Kind.SELF) {
				if (node.openable)
					tip.add(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_open")
							.withStyle(s -> s.withColor(0xA0C0FF)));
				else
					tip.add(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_jump")
							.withStyle(s -> s.withColor(0xA0C0FF)));
				if (node.kind.supportsPortCycle())
					tip.add(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_port")
							.withStyle(s -> s.withColor(0xFFE0A0)));
				tip.add(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_disconnect")
						.withStyle(s -> s.withColor(0xFFA0A0)));
				tip.add(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_drag")
						.withStyle(s -> s.withColor(0x888888)));
			} else {
				tip.add(Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.hint_wire")
						.withStyle(s -> s.withColor(0xFFE0A0)));
			}
			tooltip = tip;
		}
	}

	private static boolean showsLeftPort(Kind kind) {
		return kind == Kind.SELF || kind == Kind.OUTPUT || kind == Kind.CANDIDATE;
	}

	private static boolean showsRightPort(Kind kind) {
		return kind == Kind.SELF || kind == Kind.INPUT || kind == Kind.LINK
				|| kind == Kind.EXTRA || kind == Kind.CANDIDATE;
	}

	private void drawPortSocket(GuiGraphics g, Font font, int px, int py, boolean hover, TopoNode node, boolean left) {
		int fill = hover ? PORT_HL : (left ? 0xFF1A2838 : 0xFF382818);
		g.fill(px - 1, py - 1, px + PORT + 1, py + PORT + 1, left ? WIRE_IN : WIRE_OUT);
		g.fill(px, py, px + PORT, py + PORT, fill);
		String mark = "S";
		int tx = px + (PORT - font.width(mark)) / 2;
		g.drawString(font, mark, tx, py + 1, 0xFFFFFFFF, false);
	}

	private TopoHit hitTest(double mouseX, double mouseY) {
		for (int i = nodes.size() - 1; i >= 0; i--) {
			TopoNode node = nodes.get(i);
			if (!visibleInConnectMode(node))
				continue;
			int x = originX + Math.round(node.x);
			int y = originY + Math.round(node.y);
			if (node.kind.hasBoardSlot()) {
				int bx = x + NODE - PORT - 1;
				int by = y + NODE - PORT - 1;
				if (mouseX >= bx && mouseX < bx + PORT && mouseY >= by && mouseY < by + PORT)
					return new TopoHit(node, HitPart.BADGE);
			}
			boolean showLeft = showsLeftPort(node.kind);
			boolean showRight = showsRightPort(node.kind);
			int lx = x - PORT + 1;
			int rx = x + NODE - 1;
			int py = y + (NODE - PORT) / 2;
			if (showLeft && mouseX >= lx && mouseX < lx + PORT && mouseY >= py && mouseY < py + PORT)
				return new TopoHit(node, HitPart.LEFT);
			if (showRight && mouseX >= rx && mouseX < rx + PORT && mouseY >= py && mouseY < py + PORT)
				return new TopoHit(node, HitPart.RIGHT);
			if (mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE)
				return new TopoHit(node, HitPart.BODY);
		}
		return null;
	}

	private void openConfig(TopoNode node) {
		Level level = level();
		if (level == null)
			return;
		BlockPos pos = node.linkPos != null ? node.linkPos
				: (node.panelPos != null ? node.panelPos.pos() : null);

		if (node.kind == Kind.EXTRA || node.kind == Kind.LINK) {
			if (pos == null)
				return;
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof SignBlockEntity sign) {
				clickSound();
				Minecraft.getInstance().setScreen(new SignEditScreen(sign, true,
						Minecraft.getInstance().isTextFilteringEnabled()));
				return;
			}
			if (be instanceof DisplayLinkBlockEntity link) {
				clickSound();
				ScreenOpener.open(new DisplayLinkScreen(link));
				return;
			}
			// No screen — jump: close GUI so player can see/interact with the block
			clickSound();
			jumpToBlock(pos);
			return;
		}

		if (node.panelPos == null)
			return;
		FactoryPanelBehaviour remote = FactoryPanelBehaviour.at(level, node.panelPos);
		if (remote == null) {
			jumpToBlock(node.panelPos.pos());
			return;
		}
		clickSound();
		if (remote instanceof AbstractPanelBehaviour apb) {
			apb.displayScreen(Minecraft.getInstance().player);
		} else {
			ScreenOpener.open(new FactoryPanelScreen(remote));
		}
	}

	private void jumpToBlock(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(null);
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.translatable(
					GaugeEnhancement.MOD_ID + ".gui.network_topology.jumped",
					pos.getX(), pos.getY(), pos.getZ()), true);
		}
	}

	private void disconnect(TopoNode node) {
		if ((node.kind == Kind.INPUT || node.kind == Kind.LINK || node.kind == Kind.EXTRA)
				&& node.panelPos != null) {
			sendRemove(behaviour, node.panelPos);
		} else if (node.kind == Kind.OUTPUT && node.panelPos != null) {
			FactoryPanelBehaviour dest = FactoryPanelBehaviour.at(level(), node.panelPos);
			if (dest != null)
				sendRemove(dest, behaviour.getPanelPosition());
		}
		layout.remove(node.key);
	}

	private void cyclePort(TopoNode node) {
		if (node.panelPos == null || !node.kind.supportsPortCycle())
			return;
		Level level = level();
		if (level == null)
			return;
		PanelSlot next = nextActiveSlot(level, node.panelPos.pos(), node.panelPos.slot());
		if (next == null || next == node.panelPos.slot())
			return;

		FactoryPanelPosition newPos = new FactoryPanelPosition(node.panelPos.pos(), next);
		if (node.kind == Kind.INPUT) {
			sendRemove(behaviour, node.panelPos);
			AllPackets.getChannel().sendToServer(
					new FactoryPanelConnectionPacket(newPos, behaviour.getPanelPosition(), false));
		} else if (node.kind == Kind.OUTPUT) {
			FactoryPanelBehaviour dest = FactoryPanelBehaviour.at(level, node.panelPos);
			if (dest != null)
				sendRemove(dest, behaviour.getPanelPosition());
			AllPackets.getChannel().sendToServer(
					new FactoryPanelConnectionPacket(behaviour.getPanelPosition(), newPos, false));
		}
		float[] pos = layout.remove(node.key);
		String newKey = (node.kind == Kind.INPUT ? "in:" : "out:") + posKey(newPos);
		if (pos != null)
			layout.put(newKey, pos);
	}

	private PanelSlot nextActiveSlot(Level level, BlockPos pos, PanelSlot current) {
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof FactoryPanelBlockEntity fpbe))
			return null;
		PanelSlot[] values = PanelSlot.values();
		int start = current.ordinal();
		for (int i = 1; i <= values.length; i++) {
			PanelSlot candidate = values[(start + i) % values.length];
			FactoryPanelBehaviour b = fpbe.panels.get(candidate);
			if (b != null && b.isActive())
				return candidate;
		}
		return null;
	}

	private void sendRemove(FactoryPanelBehaviour host, FactoryPanelPosition toRemove) {
		Map<FactoryPanelPosition, Integer> amounts = new HashMap<>();
		for (Map.Entry<FactoryPanelPosition, FactoryPanelConnection> e : host.targetedBy.entrySet()) {
			if (!e.getKey().equals(toRemove))
				amounts.put(e.getKey(), e.getValue().amount);
		}
		AllPackets.getChannel().sendToServer(new FactoryPanelConfigurationPacket(
				host.getPanelPosition(),
				host.recipeAddress == null ? "" : host.recipeAddress,
				amounts,
				host.activeCraftingArrangement == null ? List.of() : host.activeCraftingArrangement,
				host.recipeOutput,
				host.promiseClearingInterval,
				toRemove,
				false, false, false));
	}

	private void ensureLayout(String key, float defX, float defY) {
		layout.computeIfAbsent(key, k -> new float[]{defX, defY});
	}

	private TopoNode findNode(String key) {
		for (TopoNode n : nodes)
			if (n.key.equals(key))
				return n;
		return null;
	}

	private Level level() {
		Level level = behaviour.getWorld();
		return level != null ? level : Minecraft.getInstance().level;
	}

	private static final List<PortType> TYPE_PRIORITY = List.of(
			PortType.INTEGER, PortType.REDSTONE, PortType.FILTER, PortType.STRING);

	private static boolean provides(AbstractPanelBehaviour apb, PortType t) {
		PanelConnection<?> pc = t.connection();
		return pc != null && apb.hasConnection(pc);
	}

	private static PortType primaryPortType(FactoryPanelBehaviour b) {
		if (b instanceof AbstractPanelBehaviour apb) {
			for (PortType t : TYPE_PRIORITY) {
				if (provides(apb, t))
					return t;
			}
		}
		return PortType.FILTER;
	}

	private static PortType sharedPortType(FactoryPanelBehaviour from, FactoryPanelBehaviour to) {
		if (!(from instanceof AbstractPanelBehaviour aa))
			return to instanceof AbstractPanelBehaviour ? primaryPortType(to) : PortType.FILTER;
		if (!(to instanceof AbstractPanelBehaviour bb))
			return primaryPortType(aa);
		PortType toPri = primaryPortType(bb);
		if (toPri == PortType.STRING && provides(aa, PortType.STRING))
			return PortType.STRING;
		if ((toPri == PortType.INTEGER || toPri == PortType.REDSTONE) && provides(aa, PortType.INTEGER))
			return PortType.INTEGER;
		if (toPri == PortType.REDSTONE && provides(aa, PortType.REDSTONE))
			return PortType.REDSTONE;
		if (toPri == PortType.FILTER && provides(aa, PortType.FILTER))
			return PortType.FILTER;
		for (PortType t : TYPE_PRIORITY) {
			if (provides(aa, t) && provides(bb, t))
				return t;
		}
		return primaryPortType(aa);
	}

	private static PortType extraPortType(Block block) {
		for (PortType t : TYPE_PRIORITY) {
			PanelConnection<?> pc = t.connection();
			if (pc != null && pc.getListener(block) != null)
				return t;
		}
		return PortType.UNKNOWN;
	}

	private static int wireColor(PortType type, int fallback) {
		return switch (type) {
			case STRING -> 0xFF70C070;
			case INTEGER -> 0xFF5BA3E0;
			case REDSTONE -> 0xFFE05050;
			case FILTER -> 0xFFE0A050;
			case LINK -> 0xFFB070E0;
			default -> fallback;
		};
	}

	private static Component displayName(FactoryPanelBehaviour b) {
		if (b instanceof AbstractPanelBehaviour apb)
			return apb.getDisplayName();
		if (b != null) {
			ItemStack filter = b.getFilter();
			if (!filter.isEmpty())
				return filter.getHoverName();
			return AllBlocks.FACTORY_GAUGE.get().getName();
		}
		return Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.unknown");
	}

	private static ItemStack iconFor(FactoryPanelBehaviour b) {
		if (b instanceof AbstractPanelBehaviour apb)
			return new ItemStack(apb.getItem());
		if (b != null) {
			ItemStack filter = b.getFilter();
			if (!filter.isEmpty())
				return filter.copy();
		}
		return AllBlocks.FACTORY_GAUGE.asStack();
	}

	private static ItemStack linkIcon(Level level, FactoryPanelConnection c) {
		FactoryPanelSupportBehaviour link = FactoryPanelBehaviour.linkAt(level, c);
		if (link != null)
			return new ItemStack(link.blockEntity.getBlockState().getBlock().asItem());
		return new ItemStack(level.getBlockState(c.from.pos()).getBlock().asItem());
	}

	private static Component linkName(Level level, FactoryPanelConnection c) {
		FactoryPanelSupportBehaviour link = FactoryPanelBehaviour.linkAt(level, c);
		if (link != null)
			return link.blockEntity.getBlockState().getBlock().getName();
		return level.getBlockState(c.from.pos()).getBlock().getName();
	}

	private static String posKey(FactoryPanelPosition p) {
		return p.pos().asLong() + ":" + p.slot().ordinal();
	}

	private static String slotLetter(PanelSlot slot) {
		return switch (slot) {
			case TOP_LEFT -> "1";
			case TOP_RIGHT -> "2";
			case BOTTOM_LEFT -> "3";
			case BOTTOM_RIGHT -> "4";
		};
	}

	private static Component slotName(PanelSlot slot) {
		String suffix = switch (slot) {
			case TOP_LEFT -> "top_left";
			case TOP_RIGHT -> "top_right";
			case BOTTOM_LEFT -> "bottom_left";
			case BOTTOM_RIGHT -> "bottom_right";
		};
		return Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.slot." + suffix);
	}

	private static void clickSound() {
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.25f));
	}

	private enum PortType {
		STRING, INTEGER, REDSTONE, FILTER, LINK, UNKNOWN;

		String mark() {
			return switch (this) {
				case STRING -> "S";
				case INTEGER -> "I";
				case REDSTONE -> "R";
				case FILTER -> "F";
				case LINK -> "L";
				default -> "?";
			};
		}

		String shortLabel() {
			return switch (this) {
				case STRING -> "STRING";
				case INTEGER -> "INTEGER";
				case REDSTONE -> "REDSTONE";
				case FILTER -> "FILTER";
				case LINK -> "LINK";
				default -> "";
			};
		}

		Component fullLabel() {
			String key = switch (this) {
				case STRING -> "string";
				case INTEGER -> "integer";
				case REDSTONE -> "redstone";
				case FILTER -> "filter";
				case LINK -> "link";
				default -> "unknown";
			};
			return Component.translatable(GaugeEnhancement.MOD_ID + ".gui.network_topology.port." + key);
		}

		PanelConnection<?> connection() {
			return switch (this) {
				case STRING -> EGPanelConnections.STRING.get();
				case INTEGER -> EGPanelConnections.INTEGER.get();
				case REDSTONE -> EGPanelConnections.REDSTONE.get();
				case FILTER -> EGPanelConnections.FILTER.get();
				default -> null;
			};
		}
	}

	private enum Kind {
		SELF, INPUT, OUTPUT, LINK, EXTRA, CANDIDATE;

		boolean supportsPortCycle() {
			return this == INPUT || this == OUTPUT;
		}

		/** True only for gauges sitting on a factory panel board (4 quadrants). */
		boolean hasBoardSlot() {
			return this == SELF || this == INPUT || this == OUTPUT || this == CANDIDATE;
		}
	}

	private enum HitPart {
		BODY, BADGE, LEFT, RIGHT
	}

	private record TopoNode(String key, Kind kind, FactoryPanelPosition panelPos, BlockPos linkPos,
							ItemStack icon, Component label, float x, float y, PanelSlot slot,
							PortType portType, boolean openable) {
	}

	private record TopoWire(TopoNode from, TopoNode to, int color, PortType type,
							String orderKey, boolean orderable, int orderIndex,
							int labelX, int labelY, int labelW, int labelH) {
	}

	private record TopoHit(TopoNode node, HitPart part) {
	}

	private static final class WireDrag {
		final String fromKey;
		final boolean fromLeft;
		final boolean pickupExisting;
		double curX;
		double curY;

		WireDrag(String fromKey, boolean fromLeft, boolean pickupExisting, double x, double y) {
			this.fromKey = fromKey;
			this.fromLeft = fromLeft;
			this.pickupExisting = pickupExisting;
			this.curX = x;
			this.curY = y;
		}
	}
}

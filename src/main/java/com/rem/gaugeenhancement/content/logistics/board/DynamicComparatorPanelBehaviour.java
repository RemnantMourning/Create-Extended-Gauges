package com.rem.gaugeenhancement.content.logistics.board;

import com.mojang.serialization.Codec;
import com.rem.gaugeenhancement.registry.ModItems;
import com.rem.gaugeenhancement.registry.ModPartialModels;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.liukrast.eg.ExtraGaugesConfig;
import net.liukrast.eg.api.logistics.board.AbstractPanelBehaviour;
import net.liukrast.eg.api.registry.PanelType;
import net.liukrast.eg.api.util.CacheContainer;
import net.liukrast.eg.api.util.ConnectionExtra;
import net.liukrast.eg.content.logistics.IntSelectorBlockEntity;
import net.liukrast.eg.content.logistics.board.ScrollOptionPanelBehaviour;
import net.liukrast.eg.registry.EGPanelConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compares the first two connected integer sources (panel gauges, Integer Selector, etc.)
 * using the same operators as Extra Gauges' Comparator Gauge.
 */
public class DynamicComparatorPanelBehaviour extends ScrollOptionPanelBehaviour<DynamicComparatorMode>
		implements CacheContainer<Integer> {

	/** Same teal used by EG Comparator / Int Selector wires (0x6496). */
	private static final int PATH_INTEGER = 25750;
	private static final int PATH_CHANGED = 16766273;
	private static final int PATH_REDSTONE_OFF = 5767425;
	private static final int PATH_REDSTONE_ON = 15663104;

	private int updated;
	private final Map<BlockPos, Integer> cache = new HashMap<>();

	public DynamicComparatorPanelBehaviour(PanelType<?> type, FactoryPanelBlockEntity be, FactoryPanelBlock.PanelSlot slot) {
		super(Component.translatable("create_gauge_enhancement.logistics.dynamic_comparator"),
				type, be, slot, DynamicComparatorMode.class);
	}

	@Override
	public Map<BlockPos, Integer> cacheMap() {
		return cache;
	}

	@Override
	public Codec<Integer> cacheCodec() {
		return Codec.INT;
	}

	@Override
	public void addConnections(AbstractPanelBehaviour.PanelConnectionBuilder builder) {
		// Match EG Comparator: REDSTONE + STRING only (integer out via redstone 0/15)
		builder.put(EGPanelConnections.REDSTONE, this::redstoneOut);
		builder.put(EGPanelConnections.STRING.get(), () -> getDisplayLinkComponent(false).getString());
	}

	private Integer redstoneOut() {
		return redstonePowered ? 0 : 15;
	}

	@Override
	public Item getItem() {
		return ModItems.DYNAMIC_COMPARATOR_GAUGE.get();
	}

	@Override
	public PartialModel getModel(FactoryPanelBlock.PanelState panelState, FactoryPanelBlock.PanelType panelType) {
		return ModPartialModels.DYNAMIC_COMPARATOR_PANEL;
	}

	@Override
	public void tick() {
		super.tick();
		updated = 0;
	}

	@Override
	public boolean shouldRenderBulb(boolean first) {
		return true;
	}

	@Override
	public void checkForRedstoneInput() {
		if (!active || updated > ExtraGaugesConfig.COMPARATOR_MAX_CHAIN.get())
			return;

		List<Integer> values = new ArrayList<>();
		consumeForLinks(support -> collectFromSupport(values, support));
		consumeForExtra(EGPanelConnections.INTEGER.get(), (pos, value) -> {
			values.add(value);
			cache.put(pos, value);
		});
		consumeForPanels(EGPanelConnections.INTEGER.get(), values::add);
		sendCache(this);

		boolean satisfied = values.size() >= 2 && get().test(values.get(0), values.get(1));

		// EG encoding: redstonePowered == !satisfied; only update when out of sync
		if (satisfied != redstonePowered)
			return;

		redstonePowered = !satisfied;
		blockEntity.notifyUpdate();
		updated++;

		for (FactoryPanelPosition targeted : targeting) {
			Level world = getWorld();
			if (!world.isLoaded(targeted.pos()))
				return;
			FactoryPanelBehaviour at = FactoryPanelBehaviour.at(world, targeted);
			if (at != null)
				at.checkForRedstoneInput();
		}
		notifyRedstoneOutputs();
	}

	private static void collectFromSupport(List<Integer> values, FactoryPanelSupportBehaviour support) {
		SmartBlockEntity be = support.blockEntity;
		if (support.shouldPanelBePowered() && be instanceof RedstoneLinkBlockEntity link) {
			values.add(link.getReceivedSignal());
			return;
		}
		if (be instanceof IntSelectorBlockEntity selector) {
			values.add(selector.behaviour.getValue());
			return;
		}
		values.add(support.shouldPanelBePowered() ? 1 : 0);
	}

	@Override
	public MutableComponent getDisplayLinkComponent(boolean shortened) {
		boolean on = getConnectionValue(EGPanelConnections.REDSTONE).orElse(0) > 0;
		String yes = shortened ? "?" : "§atrue";
		String no = shortened ? "?" : "§cfalse";
		return Component.literal(on ? yes : no);
	}

	@Override
	public int calculatePath(FactoryPanelBehaviour from, int original) {
		// Same as EG Comparator: integer sources get teal wire
		Optional<Integer> integer = EGPanelConnections.getConnectionValue(from, EGPanelConnections.INTEGER);
		if (integer.isPresent())
			return PATH_INTEGER;
		// Logic / redstone gauges → power-colored wire (same as EG renderer)
		Optional<Integer> redstone = EGPanelConnections.getConnectionValue(from, EGPanelConnections.REDSTONE);
		if (redstone.isPresent())
			return redstone.get() == 0 ? PATH_REDSTONE_OFF : PATH_REDSTONE_ON;
		return super.calculatePath(from, original);
	}

	@Override
	public int calculateExtraPath(BlockPos pos) {
		// Match EG ComparatorPanelBehaviour.calculateExtraPath exactly
		Level world = getWorld();
		BlockState state = world.getBlockState(pos);
		BlockEntity be = world.getBlockEntity(pos);

		ConnectionExtra<Integer> intListener = EGPanelConnections.INTEGER.get().getListener(state.getBlock());
		if (intListener != null) {
			Optional<Integer> opt = intListener.invalidate(world, state, pos, be);
			Integer cached = cache.get(pos);
			if (opt.isPresent()) {
				if (ExtraGaugesConfig.PANEL_CACHING.get() && opt.get().equals(cached))
					return PATH_INTEGER;
				if (ExtraGaugesConfig.PANEL_CACHING.get() && cached != null && !opt.get().equals(cached))
					return PATH_CHANGED;
				return PATH_INTEGER;
			}
		}

		ConnectionExtra<Integer> redstoneListener = EGPanelConnections.REDSTONE.get().getListener(state.getBlock());
		if (redstoneListener == null)
			return super.calculateExtraPath(pos);

		return redstoneListener.invalidate(world, state, pos, be)
				.map(v -> pathColorForRedstone(pos, v))
				.orElseGet(() -> super.calculateExtraPath(pos));
	}

	private int pathColorForRedstone(BlockPos pos, Integer value) {
		boolean on = value != 0;
		Integer cached = cache.get(pos);
		if (ExtraGaugesConfig.PANEL_CACHING.get() && cached != null) {
			boolean cachedOn = cached > 0;
			if (on == cachedOn)
				return PATH_CHANGED;
		}
		return on ? PATH_REDSTONE_ON : PATH_REDSTONE_OFF;
	}
}

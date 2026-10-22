package com.rem.gaugeenhancement.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.rem.gaugeenhancement.connection.IConnectionOrder;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import net.liukrast.eg.api.logistics.board.PanelConnection;
import net.liukrast.eg.api.util.IFPExtra;
import net.liukrast.eg.content.logistics.DisplayCollectorBlockEntity;
import net.liukrast.eg.content.logistics.board.StringPanelBehaviour;
import net.liukrast.eg.registry.EGPanelConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Join string inputs in Gauge Enhancement connection order (STR 1/2/3…),
 * instead of fixed links→extras→panels HashMap order.
 */
@Mixin(value = StringPanelBehaviour.class, remap = false)
public class StringPanelBehaviourJoinMixin {

	@SuppressWarnings("rawtypes")
	@Redirect(method = "checkForRedstoneInput", at = @At(value = "INVOKE",
			target = "Lnet/liukrast/eg/content/logistics/board/StringPanelBehaviour;consumeForLinks(Ljava/util/function/Consumer;)V"))
	private void gaugeEnhancement$skipLinks(StringPanelBehaviour self, Consumer consumer) {
	}

	@SuppressWarnings("rawtypes")
	@Redirect(method = "checkForRedstoneInput", at = @At(value = "INVOKE",
			target = "Lnet/liukrast/eg/content/logistics/board/StringPanelBehaviour;consumeForExtra(Lnet/liukrast/eg/api/logistics/board/PanelConnection;Ljava/util/function/BiConsumer;)V"))
	private void gaugeEnhancement$skipExtra(StringPanelBehaviour self, PanelConnection connection,
			BiConsumer consumer) {
	}

	@SuppressWarnings("rawtypes")
	@Redirect(method = "checkForRedstoneInput", at = @At(value = "INVOKE",
			target = "Lnet/liukrast/eg/content/logistics/board/StringPanelBehaviour;consumeForPanels(Lnet/liukrast/eg/api/logistics/board/PanelConnection;Ljava/util/function/Consumer;)V"))
	private void gaugeEnhancement$skipPanels(StringPanelBehaviour self, PanelConnection connection,
			Consumer consumer) {
	}

	@Inject(method = "checkForRedstoneInput", at = @At(value = "INVOKE",
			target = "Ljava/util/StringJoiner;toString()Ljava/lang/String;"))
	private void gaugeEnhancement$fillOrdered(CallbackInfo ci, @Local(ordinal = 0) StringJoiner joiner) {
		StringPanelBehaviour self = (StringPanelBehaviour) (Object) this;
		if (!(self instanceof IConnectionOrder order))
			return;

		List<Map.Entry<String, String>> entries = new ArrayList<>();
		Level level = self.getWorld();
		if (level == null)
			return;

		for (FactoryPanelConnection c : self.targetedByLinks.values()) {
			FactoryPanelSupportBehaviour link = FactoryPanelBehaviour.linkAt(level, c);
			if (link == null || !link.isOutput())
				continue;
			if (link.blockEntity instanceof DisplayCollectorBlockEntity collector
					&& collector.getComponent() != null) {
				String text = collector.getComponent().getString();
				String key = IConnectionOrder.keyLink(c.from.pos());
				entries.add(new AbstractMap.SimpleEntry<>(key, text));
				self.cacheMap().put(c.from.pos(), text);
			}
		}

		if (self instanceof IFPExtra ifp) {
			for (FactoryPanelConnection c : ifp.extra_gauges$getExtra().values()) {
				BlockPos pos = c.from.pos();
				if (!level.isLoaded(pos))
					continue;
				BlockState state = level.getBlockState(pos);
				BlockEntity be = level.getBlockEntity(pos);
				var listener = EGPanelConnections.STRING.get().getListener(state.getBlock());
				if (listener == null)
					continue;
				Optional<?> value = listener.invalidate(level, state, pos, be);
				if (value.isEmpty())
					continue;
				String str = String.valueOf(value.get());
				String key = IConnectionOrder.keyExtra(pos);
				entries.add(new AbstractMap.SimpleEntry<>(key, str));
				self.cacheMap().put(pos, str);
			}
		}

		for (FactoryPanelConnection c : self.targetedBy.values()) {
			FactoryPanelBehaviour src = FactoryPanelBehaviour.at(level, c);
			if (src == null)
				continue;
			Optional<String> value = EGPanelConnections.getConnectionValue(src, EGPanelConnections.STRING);
			if (value.isEmpty())
				continue;
			String key = IConnectionOrder.keyPanel(c.from);
			entries.add(new AbstractMap.SimpleEntry<>(key, value.get()));
		}

		List<String> keys = entries.stream().map(Map.Entry::getKey).toList();
		order.gaugeEnhancement$ensureOrders(keys);
		entries.sort(Comparator.comparingInt(e -> order.gaugeEnhancement$getOrder(e.getKey())));
		for (Map.Entry<String, String> e : entries)
			joiner.add(e.getValue());
	}
}

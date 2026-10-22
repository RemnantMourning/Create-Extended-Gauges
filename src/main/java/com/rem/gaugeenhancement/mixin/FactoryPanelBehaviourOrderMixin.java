package com.rem.gaugeenhancement.mixin;

import com.rem.gaugeenhancement.connection.IConnectionOrder;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.utility.CreateLang;
import net.liukrast.eg.api.logistics.board.AbstractPanelBehaviour;
import net.liukrast.eg.api.util.IFPExtra;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class FactoryPanelBehaviourOrderMixin implements IConnectionOrder {

	@Unique
	private final Map<String, Integer> gaugeEnhancement$order = new HashMap<>();

	@Override
	public Map<String, Integer> gaugeEnhancement$getOrderMap() {
		return gaugeEnhancement$order;
	}

	@Inject(method = "addConnection", at = @At("TAIL"))
	private void gaugeEnhancement$assignOrder(FactoryPanelPosition fromPos, CallbackInfo ci) {
		FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
		String key;
		if (self.targetedBy.containsKey(fromPos))
			key = IConnectionOrder.keyPanel(fromPos);
		else if (self.targetedByLinks.containsKey(fromPos.pos()))
			key = IConnectionOrder.keyLink(fromPos.pos());
		else if (self instanceof IFPExtra extra && extra.extra_gauges$getExtra().containsKey(fromPos.pos()))
			key = IConnectionOrder.keyExtra(fromPos.pos());
		else
			key = IConnectionOrder.keyPanel(fromPos);

		if (!gaugeEnhancement$order.containsKey(key) || gaugeEnhancement$order.get(key) <= 0) {
			int max = gaugeEnhancement$order.values().stream().mapToInt(Integer::intValue).max().orElse(0);
			gaugeEnhancement$order.put(key, max + 1);
		}
	}

	/**
	 * Vanilla Create gauges only. EG panels overwrite this NBT in {@code AbstractPanelBehaviour.write};
	 * their order is persisted via {@link AbstractPanelBehaviourOrderPersistMixin}.
	 */
	@Inject(method = "write", at = @At("TAIL"))
	private void gaugeEnhancement$writeOrder(CompoundTag nbt, boolean clientPacket, CallbackInfo ci) {
		if ((Object) this instanceof AbstractPanelBehaviour)
			return;
		FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
		String panelKey = CreateLang.asId(self.slot.name());
		if (!nbt.contains(panelKey) || gaugeEnhancement$order.isEmpty())
			return;
		ListTag list = new ListTag();
		for (Map.Entry<String, Integer> e : gaugeEnhancement$order.entrySet()) {
			CompoundTag tag = new CompoundTag();
			tag.putString("K", e.getKey());
			tag.putInt("O", e.getValue());
			list.add(tag);
		}
		nbt.getCompound(panelKey).put("GaugeEnhancementOrder", list);
	}

	@Inject(method = "read", at = @At("TAIL"))
	private void gaugeEnhancement$readOrder(CompoundTag nbt, boolean clientPacket, CallbackInfo ci) {
		// EG: AbstractPanelBehaviour.read calls super then easyRead — skip clear here.
		if ((Object) this instanceof AbstractPanelBehaviour)
			return;
		gaugeEnhancement$order.clear();
		FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
		String panelKey = CreateLang.asId(self.slot.name());
		if (!nbt.contains(panelKey))
			return;
		CompoundTag panelTag = nbt.getCompound(panelKey);
		if (!panelTag.contains("GaugeEnhancementOrder", Tag.TAG_LIST))
			return;
		ListTag list = panelTag.getList("GaugeEnhancementOrder", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag tag = list.getCompound(i);
			gaugeEnhancement$order.put(tag.getString("K"), tag.getInt("O"));
		}
	}

	@Inject(method = "disconnectAll", at = @At("TAIL"))
	private void gaugeEnhancement$clearOrderAll(CallbackInfo ci) {
		gaugeEnhancement$order.clear();
	}

	@Inject(method = "disconnectAllLinks", at = @At("TAIL"))
	private void gaugeEnhancement$clearLinkOrders(CallbackInfo ci) {
		gaugeEnhancement$order.keySet().removeIf(k -> k.startsWith("l:") || k.startsWith("e:"));
	}
}

package com.rem.gaugeenhancement.mixin;

import com.rem.gaugeenhancement.connection.IConnectionOrder;
import net.liukrast.eg.api.logistics.board.AbstractPanelBehaviour;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * EG panels rebuild the slot NBT after {@code FactoryPanelBehaviour.write}, wiping any
 * tags we attach there. Persist order through {@code easyWrite}/{@code easyRead} instead.
 */
@Mixin(value = AbstractPanelBehaviour.class, remap = false)
public class AbstractPanelBehaviourOrderPersistMixin {

	@Inject(method = "easyWrite", at = @At("TAIL"))
	private void gaugeEnhancement$writeOrder(CompoundTag panelTag, boolean clientPacket, CallbackInfo ci) {
		if (!(this instanceof IConnectionOrder order))
			return;
		Map<String, Integer> map = order.gaugeEnhancement$getOrderMap();
		if (map.isEmpty())
			return;
		ListTag list = new ListTag();
		for (Map.Entry<String, Integer> e : map.entrySet()) {
			CompoundTag tag = new CompoundTag();
			tag.putString("K", e.getKey());
			tag.putInt("O", e.getValue());
			list.add(tag);
		}
		panelTag.put("GaugeEnhancementOrder", list);
	}

	@Inject(method = "easyRead", at = @At("HEAD"))
	private void gaugeEnhancement$readOrder(CompoundTag panelTag, boolean clientPacket, CallbackInfo ci) {
		if (!(this instanceof IConnectionOrder order))
			return;
		Map<String, Integer> map = order.gaugeEnhancement$getOrderMap();
		map.clear();
		if (!panelTag.contains("GaugeEnhancementOrder", Tag.TAG_LIST))
			return;
		ListTag list = panelTag.getList("GaugeEnhancementOrder", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag tag = list.getCompound(i);
			map.put(tag.getString("K"), tag.getInt("O"));
		}
	}
}

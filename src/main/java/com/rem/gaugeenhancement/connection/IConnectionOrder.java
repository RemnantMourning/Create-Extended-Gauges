package com.rem.gaugeenhancement.connection;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Per-panel connection display/merge order (1-based), separate from Create's {@code amount}.
 */
public interface IConnectionOrder {

	Map<String, Integer> gaugeEnhancement$getOrderMap();

	static String keyPanel(FactoryPanelPosition pos) {
		return "p:" + pos.pos().asLong() + ":" + pos.slot().ordinal();
	}

	static String keyExtra(BlockPos pos) {
		return "e:" + pos.asLong();
	}

	static String keyLink(BlockPos pos) {
		return "l:" + pos.asLong();
	}

	static String keyOf(FactoryPanelConnection connection, ConnKind kind) {
		return switch (kind) {
			case PANEL -> keyPanel(connection.from);
			case EXTRA -> keyExtra(connection.from.pos());
			case LINK -> keyLink(connection.from.pos());
		};
	}

	default int gaugeEnhancement$getOrder(String key) {
		return gaugeEnhancement$getOrderMap().getOrDefault(key, 0);
	}

	default void gaugeEnhancement$setOrder(String key, int order) {
		gaugeEnhancement$getOrderMap().put(key, order);
	}

	default void gaugeEnhancement$ensureOrders(List<String> keysInPreferredOrder) {
		Map<String, Integer> map = gaugeEnhancement$getOrderMap();
		// Only drop stale keys that belong to this same group — never wipe other groups
		// (panel/extra/link or STRING/INT) when ensuring a partial list.
		if (!keysInPreferredOrder.isEmpty()) {
			String prefix = groupPrefix(keysInPreferredOrder.get(0));
			map.keySet().removeIf(k -> k.startsWith(prefix) && !keysInPreferredOrder.contains(k));
		}
		int next = 1;
		for (String key : keysInPreferredOrder) {
			if (!map.containsKey(key) || map.get(key) <= 0)
				map.put(key, next);
			next = Math.max(next, map.get(key) + 1);
		}
		// Compact to 1..n preserving relative order within this group only
		List<String> sorted = new ArrayList<>(keysInPreferredOrder);
		sorted.sort(Comparator.comparingInt(k -> map.getOrDefault(k, Integer.MAX_VALUE)));
		for (int i = 0; i < sorted.size(); i++)
			map.put(sorted.get(i), i + 1);
	}

	static String groupPrefix(String key) {
		int colon = key.indexOf(':');
		return colon >= 0 ? key.substring(0, colon + 1) : key;
	}

	default void gaugeEnhancement$moveToIndex(String key, int newIndex1Based, List<String> groupKeys) {
		Map<String, Integer> map = gaugeEnhancement$getOrderMap();
		gaugeEnhancement$ensureOrders(groupKeys);
		List<String> sorted = new ArrayList<>(groupKeys);
		sorted.sort(Comparator.comparingInt(k -> map.getOrDefault(k, Integer.MAX_VALUE)));
		if (!sorted.remove(key))
			return;
		int idx = Math.max(0, Math.min(sorted.size(), newIndex1Based - 1));
		sorted.add(idx, key);
		for (int i = 0; i < sorted.size(); i++)
			map.put(sorted.get(i), i + 1);
	}

	default List<FactoryPanelConnection> gaugeEnhancement$sorted(Collection<FactoryPanelConnection> connections, ConnKind kind) {
		List<FactoryPanelConnection> list = new ArrayList<>(connections);
		List<String> keys = new ArrayList<>();
		for (FactoryPanelConnection c : list)
			keys.add(keyOf(c, kind));
		gaugeEnhancement$ensureOrders(keys);
		Map<String, Integer> map = gaugeEnhancement$getOrderMap();
		list.sort(Comparator.comparingInt(c -> map.getOrDefault(keyOf(c, kind), Integer.MAX_VALUE)));
		return list;
	}

	enum ConnKind {PANEL, EXTRA, LINK}
}

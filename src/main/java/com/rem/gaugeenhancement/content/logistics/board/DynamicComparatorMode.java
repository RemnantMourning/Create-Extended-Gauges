package com.rem.gaugeenhancement.content.logistics.board;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.lang.Lang;
import net.liukrast.eg.content.EGIcons;

public enum DynamicComparatorMode implements INamedIconOptions {
	EQUALS(EGIcons.I_EQUALS, (a, b) -> a == b),
	DIFFERENT(EGIcons.I_DIFFERENT, (a, b) -> a != b),
	GREATER(EGIcons.I_GREATER, (a, b) -> a > b),
	GREATER_EQUALS(EGIcons.I_GREATER_EQUALS, (a, b) -> a >= b),
	LESS(EGIcons.I_LESS, (a, b) -> a < b),
	LESS_EQUALS(EGIcons.I_LESS_EQUALS, (a, b) -> a <= b);

	private final String translationKey;
	private final AllIcons icon;
	private final IntBiPredicate function;

	DynamicComparatorMode(AllIcons icon, IntBiPredicate function) {
		this.icon = icon;
		this.translationKey = "create_gauge_enhancement.dynamic_comparator.mode." + Lang.asId(name());
		this.function = function;
	}

	@Override
	public AllIcons getIcon() {
		return icon;
	}

	@Override
	public String getTranslationKey() {
		return translationKey;
	}

	public boolean test(int a, int b) {
		return function.test(a, b);
	}

	@FunctionalInterface
	private interface IntBiPredicate {
		boolean test(int a, int b);
	}
}

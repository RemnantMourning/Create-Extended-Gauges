package com.rem.gaugeenhancement.mixin;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.liukrast.eg.api.logistics.board.AbstractPanelBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Soften Create's orientation check for Extra Gauges panels, but still require
 * the same attach face and the same surface plane. Bypassing {@code same_orientation}
 * without re-checking {@code same_surface} allowed cross-plane links and broke wire rendering
 * (especially Dynamic Comparator ↔ Dynamic Comparator on floors).
 */
@Mixin(value = FactoryPanelConnectionHandler.class, remap = false)
public class FactoryPanelConnectionHandlerMixin {

	private static final String SAME_ORIENTATION = "factory_panel.same_orientation";

	@Inject(
			method = "checkForIssues(Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBehaviour;Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBehaviour;)Ljava/lang/String;",
			at = @At("RETURN"),
			cancellable = true
	)
	private static void gaugeEnhancement$allowPanelOrientation(
			FactoryPanelBehaviour from,
			FactoryPanelBehaviour to,
			CallbackInfoReturnable<String> cir
	) {
		if (!SAME_ORIENTATION.equals(cir.getReturnValue()))
			return;
		if (!(from instanceof AbstractPanelBehaviour || to instanceof AbstractPanelBehaviour))
			return;

		BlockState stateTo = to.blockEntity.getBlockState();
		BlockState stateFrom = from.blockEntity.getBlockState();
		if (!stateTo.hasProperty(FactoryPanelBlock.FACE) || !stateFrom.hasProperty(FactoryPanelBlock.FACE))
			return;
		AttachFace face = stateTo.getValue(FactoryPanelBlock.FACE);
		if (face != stateFrom.getValue(FactoryPanelBlock.FACE))
			return;

		BlockPos diff = to.getPos().subtract(from.getPos());
		Direction normal = FactoryPanelBlock.connectedDirection(stateTo);
		if (normal.getAxis().choose(diff.getX(), diff.getY(), diff.getZ()) != 0)
			return;

		// Floor/ceiling: FACING is in-plane rotation — mismatch breaks wire endpoints.
		if (face != AttachFace.WALL
				&& stateTo.getValue(FactoryPanelBlock.FACING) != stateFrom.getValue(FactoryPanelBlock.FACING))
			return;

		cir.setReturnValue(null);
	}

	@Inject(
			method = "checkForIssues(Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBehaviour;Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelSupportBehaviour;)Ljava/lang/String;",
			at = @At("RETURN"),
			cancellable = true
	)
	private static void gaugeEnhancement$allowSupportOrientation(
			FactoryPanelBehaviour from,
			FactoryPanelSupportBehaviour to,
			CallbackInfoReturnable<String> cir
	) {
		if (!SAME_ORIENTATION.equals(cir.getReturnValue()))
			return;
		if (!(from instanceof AbstractPanelBehaviour))
			return;

		BlockState panelState = from.blockEntity.getBlockState();
		BlockState linkState = to.blockEntity.getBlockState();
		Direction panelNormal = FactoryPanelBlock.connectedDirection(panelState);
		Direction linkFacing = linkState.getOptionalValue(WrenchableDirectionalBlock.FACING)
				.orElse(panelNormal);
		if (panelNormal != linkFacing)
			return;

		BlockPos diff = to.getPos().subtract(from.getPos());
		if (panelNormal.getAxis().choose(diff.getX(), diff.getY(), diff.getZ()) != 0)
			return;

		cir.setReturnValue(null);
	}
}

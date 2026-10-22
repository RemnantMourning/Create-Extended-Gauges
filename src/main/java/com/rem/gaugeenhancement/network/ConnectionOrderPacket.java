package com.rem.gaugeenhancement.network;

import com.rem.gaugeenhancement.connection.IConnectionOrder;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sets the merge/display order index of one connection within its group on a host panel.
 */
public class ConnectionOrderPacket {

	private final FactoryPanelPosition host;
	private final String key;
	private final int newIndex;
	private final List<String> groupKeys;

	public ConnectionOrderPacket(FactoryPanelPosition host, String key, int newIndex, List<String> groupKeys) {
		this.host = host;
		this.key = key;
		this.newIndex = newIndex;
		this.groupKeys = groupKeys;
	}

	public static void encode(ConnectionOrderPacket pkt, FriendlyByteBuf buf) {
		buf.writeBlockPos(pkt.host.pos());
		buf.writeVarInt(pkt.host.slot().ordinal());
		buf.writeUtf(pkt.key);
		buf.writeVarInt(pkt.newIndex);
		buf.writeVarInt(pkt.groupKeys.size());
		for (String k : pkt.groupKeys)
			buf.writeUtf(k);
	}

	public static ConnectionOrderPacket decode(FriendlyByteBuf buf) {
		FactoryPanelPosition host = new FactoryPanelPosition(buf.readBlockPos(),
				PanelSlot.values()[buf.readVarInt()]);
		String key = buf.readUtf();
		int newIndex = buf.readVarInt();
		int n = buf.readVarInt();
		List<String> group = new ArrayList<>(n);
		for (int i = 0; i < n; i++)
			group.add(buf.readUtf());
		return new ConnectionOrderPacket(host, key, newIndex, group);
	}

	public static void handle(ConnectionOrderPacket pkt, Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			ServerPlayer player = ctx.get().getSender();
			if (player == null)
				return;
			FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(player.level(), pkt.host);
			if (!(behaviour instanceof IConnectionOrder order))
				return;
			if (!player.level().isLoaded(pkt.host.pos()))
				return;
			if (player.distanceToSqr(pkt.host.pos().getX() + 0.5, pkt.host.pos().getY() + 0.5,
					pkt.host.pos().getZ() + 0.5) > 64 * 64)
				return;
			order.gaugeEnhancement$moveToIndex(pkt.key, pkt.newIndex, pkt.groupKeys);
			behaviour.checkForRedstoneInput();
			behaviour.blockEntity.notifyUpdate();
		});
		ctx.get().setPacketHandled(true);
	}
}

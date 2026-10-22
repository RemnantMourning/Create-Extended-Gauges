package com.rem.gaugeenhancement.network;

import com.rem.gaugeenhancement.GaugeEnhancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {

	private static final String PROTOCOL = "1";
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			ResourceLocation.fromNamespaceAndPath(GaugeEnhancement.MOD_ID, "main"),
			() -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	private static int id;

	private ModNetwork() {
	}

	public static void register() {
		CHANNEL.registerMessage(id++, ConnectionOrderPacket.class,
				ConnectionOrderPacket::encode, ConnectionOrderPacket::decode,
				ConnectionOrderPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
	}
}

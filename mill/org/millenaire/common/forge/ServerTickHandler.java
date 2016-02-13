package org.millenaire.common.forge;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.millenaire.common.MillWorld;

public class ServerTickHandler {

	@SubscribeEvent
	public void tickStart(final TickEvent.ServerTickEvent event) {

		if (Mill.startupError) {
			return;
		}

		for (final MillWorld mw : Mill.serverWorlds) {
			mw.updateWorldServer();
		}
	}
}

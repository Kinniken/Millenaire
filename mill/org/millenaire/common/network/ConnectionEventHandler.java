package org.millenaire.common.network;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ServerDisconnectionFromClientEvent;

import org.millenaire.common.MLN;
import org.millenaire.common.MillWorld;
import org.millenaire.common.UserProfile;
import org.millenaire.common.core.MillCommonUtilities;
import org.millenaire.common.forge.Mill;

public class ConnectionEventHandler {

	@SubscribeEvent
	public void clientLoggedIn(final ClientConnectedToServerEvent event) {
		Mill.proxy.handleClientLogin();
	}

	@SubscribeEvent
	public void connectionClosed(final ServerDisconnectionFromClientEvent event) {
		// can't tell who it is in Forge right now so checking everyone

		for (final MillWorld mw : Mill.serverWorlds) {
			mw.checkConnections();
		}
	}

	@SubscribeEvent
	public void playerLoggedIn(final PlayerLoggedInEvent event) {

		try {
			final UserProfile profile = MillCommonUtilities.getServerProfile(event.player.worldObj, event.player.getName());
			if (profile != null) {
				profile.connectUser();
			} else {
				MLN.error(this, "Could not get profile on login for user: " + event.player.getName());
			}
		} catch (final Exception e) {
			MLN.printException("Error in ConnectionHandler.playerLoggedIn:", e);
		}

	}
}

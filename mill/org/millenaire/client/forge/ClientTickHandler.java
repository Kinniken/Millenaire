package org.millenaire.client.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.millenaire.client.gui.DisplayActions;
import org.millenaire.common.forge.Mill;

public class ClientTickHandler {

	private boolean startupMessageShow;

	@SubscribeEvent
	public void tickStart(final TickEvent.ClientTickEvent event) {
		if (Mill.clientWorld == null || !Mill.clientWorld.millenaireEnabled || Minecraft.getMinecraft().thePlayer == null) {
			return;
		}

		final boolean onSurface = Minecraft.getMinecraft().thePlayer.dimension == 0;

		Mill.clientWorld.updateWorldClient(onSurface);

		if (!startupMessageShow) {
			DisplayActions.displayStartupOrError(Minecraft.getMinecraft().thePlayer, Mill.startupError);
			startupMessageShow = true;
		}

		Mill.proxy.handleClientGameUpdate();
	}
}

package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.millenaire.client.network.ClientSender;
import org.millenaire.common.MLN;
import org.millenaire.common.MillVillager;
import org.millenaire.common.core.MillCommonUtilities;
import org.millenaire.common.forge.Mill;

public class GuiHire extends GuiText {

	private static final int REPUTATION_NEEDED = 64 * 64;
	public static final int BUTTON_CLOSE = 0;
	public static final int BUTTON_HIRE = 1;
	public static final int BUTTON_EXTEND = 2;
	public static final int BUTTON_RELEASE = 3;

	private final MillVillager villager;
	private final EntityPlayer player;

	ResourceLocation background = new ResourceLocation(Mill.modId, "textures/gui/ML_quest.png");

	public GuiHire(final EntityPlayer player, final MillVillager villager) {
		this.villager = villager;
		this.player = player;
	}

	@Override
	protected void actionPerformed(final GuiButton guibutton) {
		if (!guibutton.enabled) {
			return;
		}

		if (guibutton.id == BUTTON_CLOSE) {
			mc.displayGuiScreen(null);
			mc.setIngameFocus();
		} else if (guibutton.id == BUTTON_HIRE) {
			ClientSender.hireHire(player, villager);
			refreshContent();
		} else if (guibutton.id == BUTTON_EXTEND) {
			ClientSender.hireExtend(player, villager);
			refreshContent();
		} else if (guibutton.id == BUTTON_RELEASE) {
			ClientSender.hireRelease(player, villager);
			refreshContent();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void buttonPagination() {

		super.buttonPagination();

		final int xStart = (width - getXSize()) / 2;
		final int yStart = (height - getYSize()) / 2;

		if (villager.hiredBy != null) {
			if (MillCommonUtilities.countMoney(player.inventory) >= villager.getHireCost(player)) {
				buttonList.add(new GuiButton(BUTTON_EXTEND, xStart + getXSize() / 2 - 100, yStart + getYSize() - 40, 63, 20, MLN.string("hire.extend")));
			}
			buttonList.add(new GuiButton(BUTTON_RELEASE, xStart + getXSize() / 2 - 32, yStart + getYSize() - 40, 64, 20, MLN.string("hire.release")));
			buttonList.add(new GuiButton(BUTTON_CLOSE, xStart + getXSize() / 2 + 37, yStart + getYSize() - 40, 63, 20, MLN.string("hire.close")));

		} else {
			if (villager.getTownHall().getReputation(player.getName()) >= REPUTATION_NEEDED && MillCommonUtilities.countMoney(player.inventory) >= villager.getHireCost(player)) {
				buttonList.add(new GuiButton(BUTTON_HIRE, xStart + getXSize() / 2 - 100, yStart + getYSize() - 40, 95, 20, MLN.string("hire.hire")));
			}
			buttonList.add(new GuiButton(BUTTON_CLOSE, xStart + getXSize() / 2 + 5, yStart + getYSize() - 40, 95, 20, MLN.string("hire.close")));
		}

	}

	@Override
	protected void customDrawBackground(final int i, final int j, final float f) {
	}

	@Override
	protected void customDrawScreen(final int i, final int j, final float f) {
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private List<List<Line>> getData() {

		final List<Line> text = new ArrayList<Line>();

		text.add(new Line(villager.getName() + ", " + villager.getNativeOccupationName()));
		text.add(new Line());

		if (villager.hiredBy != null) {
			text.add(new Line(MLN.string("hire.hiredvillager", "" + Math.round((villager.hiredUntil - villager.worldObj.getWorldTime()) / 1000), Keyboard.getKeyName(MLN.keyAggressiveEscorts))));
		} else if (villager.getTownHall().getReputation(player.getName()) >= REPUTATION_NEEDED) {
			text.add(new Line(MLN.string("hire.hireablevillager")));
		} else {
			text.add(new Line(MLN.string("hire.hireablevillagernoreputation")));
		}
		text.add(new Line());
		text.add(new Line(MLN.string("hire.health") + ": " + villager.getHealth() * 0.5 + "/" + villager.getMaxHealth() * 0.5));
		text.add(new Line(MLN.string("hire.strength") + ": " + villager.getAttackStrength()));
		text.add(new Line(MLN.string("hire.cost") + ": " + MillCommonUtilities.getShortPrice(villager.getHireCost(player))));

		final List<List<Line>> ftext = new ArrayList<List<Line>>();
		ftext.add(text);

		return adjustText(ftext);
	}

	@Override
	public int getLineSizeInPx() {
		return 240;
	}

	@Override
	public int getPageSize() {
		return 16;
	}

	@Override
	public ResourceLocation getPNGPath() {
		return background;
	}

	@Override
	public int getXSize() {
		return 256;
	}

	@Override
	public int getYSize() {
		return 220;
	}

	@Override
	public void initData() {
		refreshContent();
	}

	private void refreshContent() {
		descText = getData();
		buttonPagination();
	}
}

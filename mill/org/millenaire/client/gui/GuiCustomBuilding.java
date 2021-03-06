package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.millenaire.client.network.ClientSender;
import org.millenaire.common.MLN;
import org.millenaire.common.Point;
import org.millenaire.common.VillageType;
import org.millenaire.common.building.Building;
import org.millenaire.common.building.BuildingCustomPlan;
import org.millenaire.common.building.BuildingCustomPlan.TypeRes;
import org.millenaire.common.forge.Mill;

/**
 * GUI to display resources found for a custom building
 * 
 * Act as a confirmation screen if needed resources available, as an error
 * explanation if not
 * 
 * @author cedricdj
 * 
 */
public class GuiCustomBuilding extends GuiText {

	private static final int BUTTON_CONFIRM = 1;
	private final Building townHall;
	private final Building existingBuilding;
	private final Point pos;
	private final VillageType villageType;
	private final BuildingCustomPlan customBuilding;
	private final EntityPlayer player;
	private final Map<TypeRes, List<Point>> resources;

	ResourceLocation background = new ResourceLocation(Mill.modId, "textures/gui/ML_panel.png");

	/**
	 * Creates a GUI to edit an existing custom building
	 * 
	 * @param player
	 * @param building
	 */
	public GuiCustomBuilding(final EntityPlayer player, final Building building) {
		this.townHall = building.getTownHall();
		existingBuilding = building;
		villageType = null;
		pos = building.getPos();
		this.player = player;
		this.customBuilding = building.location.getCustomPlan();
		this.resources = customBuilding.findResources(townHall.worldObj, pos, townHall, building.location);
	}

	/**
	 * Create a GUI to confirm the creation of a custom building
	 */
	public GuiCustomBuilding(final EntityPlayer player, final Building th, final Point p, final BuildingCustomPlan customBuilding) {

		townHall = th;
		villageType = null;
		existingBuilding = null;
		pos = p;
		this.player = player;
		this.customBuilding = customBuilding;
		this.resources = customBuilding.findResources(th.worldObj, pos, th, null);
	}

	/**
	 * Create a GUI to confirm the creation of a new village with a custom TH
	 */
	public GuiCustomBuilding(final EntityPlayer player, final Point p, final VillageType villageType) {

		townHall = null;
		existingBuilding = null;
		this.villageType = villageType;
		pos = p;
		this.player = player;
		this.customBuilding = villageType.customCentre;
		this.resources = customBuilding.findResources(player.worldObj, pos, null, null);
	}

	@Override
	protected void actionPerformed(final GuiButton guibutton) {
		if (!guibutton.enabled) {
			return;
		}

		if (guibutton.id == BUTTON_CONFIRM) {
			if (townHall != null) {
				if (existingBuilding == null) {
					ClientSender.newCustomBuilding(player, townHall, pos, customBuilding.buildingKey);
				} else {
					ClientSender.updateCustomBuilding(player, existingBuilding);
				}
			} else {
				ClientSender.newVillageCreation(player, pos, villageType.culture.key, villageType.key);
			}
			closeWindow();
		} else {
			closeWindow();
			if (townHall != null) {
				DisplayActions.displayNewBuildingProjectGUI(player, townHall, pos);
			} else {
				DisplayActions.displayNewVillageGUI(player, pos);
			}
		}

	}

	@Override
	protected void customDrawBackground(final int i, final int j, final float f) {

	}

	@Override
	protected void customDrawScreen(final int i, final int j, final float f) {

	}

	@Override
	public void decrementPage() {
		super.decrementPage();
		buttonPagination();
	}

	@Override
	public int getLineSizeInPx() {
		return 195;
	}

	@Override
	public int getPageSize() {
		return 19;
	}

	@Override
	public ResourceLocation getPNGPath() {
		return background;
	}

	@Override
	public int getXSize() {
		return 204;
	}

	@Override
	public int getYSize() {
		return 220;
	}

	@Override
	public void incrementPage() {
		super.incrementPage();
		buttonPagination();
	}

	@Override
	public void initData() {

		boolean validBuild = true;

		for (final TypeRes res : customBuilding.minResources.keySet()) {
			if (!resources.containsKey(res) || resources.get(res).size() < customBuilding.minResources.get(res)) {
				validBuild = false;
			}
		}

		final List<List<Line>> pages = new ArrayList<List<Line>>();
		final List<Line> text = new ArrayList<Line>();

		if (townHall != null) {
			text.add(new Line(townHall.getVillageQualifiedName()));
		} else {
			text.add(new Line(MLN.string("ui.custombuilding_newvillage")));
		}

		text.add(new Line());
		if (existingBuilding != null) {
			text.add(new Line(MLN.string("ui.custombuilding_edit", customBuilding.getFullDisplayName())));
		} else if (validBuild) {
			text.add(new Line(MLN.string("ui.custombuilding_confirm", customBuilding.getFullDisplayName())));
		} else {
			text.add(new Line(MLN.string("ui.custombuilding_cantconfirm", customBuilding.getFullDisplayName())));
		}

		text.add(new Line());
		text.add(new Line(MLN.string("ui.custombuilding_radius", "" + customBuilding.radius, "" + customBuilding.heightRadius)));

		if (resources.containsKey(TypeRes.SIGN) && resources.get(TypeRes.SIGN).size() > 1) {
			text.add(new Line());
			text.add(new Line(MLN.string("ui.custombuilding_signnumber", "" + resources.get(TypeRes.SIGN).size())));
		}

		text.add(new Line());
		text.add(new Line(MLN.string("ui.custombuilding_resneededintro")));
		text.add(new Line());
		for (final TypeRes res : customBuilding.minResources.keySet()) {

			int resFound = 0;
			if (resources.containsKey(res)) {
				resFound = resources.get(res).size();
			}

			text.add(new Line(MLN.string("ui.custombuilding_resneeded", MLN.string("custombuilding." + res.key), "" + resFound, "" + customBuilding.minResources.get(res), ""
					+ customBuilding.maxResources.get(res))));
		}

		text.add(new Line());

		if (validBuild) {
			text.add(new Line(new MillGuiButton(MLN.string("ui.close"), 0), new MillGuiButton(MLN.string("ui.confirm"), BUTTON_CONFIRM)));
		} else {
			text.add(new Line(new MillGuiButton(MLN.string("ui.close"), 0)));
		}

		pages.add(text);

		descText = adjustText(pages);
	}

}

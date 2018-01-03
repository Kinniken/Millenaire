package org.millenaire.common.goal.generic;

import java.io.BufferedReader;
import java.io.File;
import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

import org.millenaire.common.InvItem;
import org.millenaire.common.MLN;
import org.millenaire.common.MillVillager;
import org.millenaire.common.Point;
import org.millenaire.common.building.Building;
import org.millenaire.common.core.MillCommonUtilities;
import org.millenaire.common.item.Goods;

public class GoalGenericCooking extends GoalGeneric {

	public static GoalGenericCooking loadGenericCookingGoal(final File file) {

		final GoalGenericCooking g = new GoalGenericCooking();

		g.key = file.getName().split("\\.")[0].toLowerCase();

		try {
			final BufferedReader reader = MillCommonUtilities.getReader(file);

			String line;

			while ((line = reader.readLine()) != null) {
				if (line.trim().length() > 0 && !line.startsWith("//")) {
					final String[] temp = line.split("=");
					if (temp.length != 2) {
						MLN.error(null, "Invalid line when loading generic cooking goal " + file.getName() + ": " + line);
					} else {
						final String key = temp[0].trim().toLowerCase();
						final String value = temp[1].trim();

						if (!GoalGeneric.readGenericGoalConfigLine(g, key, value, file, line)) {
							if (key.equals("itemtocook")) {
								if (Goods.goodsName.containsKey(value)) {
									g.itemToCook = Goods.goodsName.get(value);
								} else {
									MLN.error(null, "Unknown itemToCook item in generic cooking goal " + file.getName() + ": " + line);
								}
							} else if (key.equals("minimumtocook")) {
								g.minimumToCook = Integer.parseInt(value);
							} else {
								MLN.error(null, "Unknown line in generic cooking goal " + file.getName() + ": " + line);
							}
						}
					}
				}
			}

			if (g.itemToCook == null) {
				MLN.error(null, "The itemtocook id is mandatory in custom cooking goals " + file.getName());
				return null;
			}

			reader.close();
		} catch (final Exception e) {
			MLN.printException(e);

			return null;
		}

		return g;

	}

	public InvItem itemToCook = null;

	public int minimumToCook = 16;

	@Override
	public GoalInformation getDestination(final MillVillager villager) throws Exception {

		final List<Building> buildings = getBuildings(villager);

		for (final Building dest : buildings) {

			if (isDestPossible(villager, dest)) {

				for (final Point p : dest.getResManager().furnaces) {

					final TileEntityFurnace furnace = p.getFurnace(villager.worldObj);

					if (furnace != null) {
						// check for fuel addition
						if ((furnace.getStackInSlot(1) == null || furnace.getStackInSlot(1).stackSize < 32) 
								&& dest.countGoods(Blocks.log, -1) > 4) {
							return packDest(p, dest);
						}

						// check for item addition
						if (dest.countGoods(itemToCook) >= minimumToCook
								&& (furnace.getStackInSlot(0) == null || furnace.getStackInSlot(0).getItem() == itemToCook.getItem() && furnace.getStackInSlot(0).getItemDamage() == itemToCook.meta
										&& furnace.getStackInSlot(0).stackSize < 32)) {
							return packDest(p, dest);
						}

						// check items for removal
						if (furnace.getStackInSlot(2) != null && furnace.getStackInSlot(2).stackSize >= minimumToCook) {
							return packDest(p, dest);
						}

					}
				}
			}
		}

		return null;
	}

	@Override
	public boolean isDestPossibleSpecific(final MillVillager villager, final Building b) {
		return true;
	}

	@Override
	public boolean isPossibleGenericGoal(final MillVillager villager) throws Exception {
		if (getDestination(villager) == null) {
			return false;
		}
		return true;
	}

	@Override
	public boolean performAction(final MillVillager villager) throws Exception {

		final TileEntityFurnace furnace = villager.getGoalDestPoint().getFurnace(villager.worldObj);

		final Building dest = villager.getGoalBuildingDest();

		if (furnace != null && dest != null) {
			if (furnace.getStackInSlot(0) == null && dest.countGoods(itemToCook) >= minimumToCook || furnace.getStackInSlot(0) != null && furnace.getStackInSlot(0).getItem() == itemToCook.getItem()
					&& furnace.getStackInSlot(0).getItemDamage() == itemToCook.meta && furnace.getStackInSlot(0).stackSize < 64 && dest.countGoods(itemToCook) > 0) {
				int nb;
				if (furnace.getStackInSlot(0) == null) {
					nb = Math.min(64, dest.countGoods(itemToCook));

					furnace.setInventorySlotContents(0, new ItemStack(itemToCook.getItem(), nb, itemToCook.meta));
					dest.takeGoods(itemToCook, nb);
				} else {
					nb = Math.min(64 - furnace.getStackInSlot(0).stackSize, villager.getHouse().countGoods(itemToCook));
					furnace.getStackInSlot(0).stackSize = furnace.getStackInSlot(0).stackSize + nb;
					dest.takeGoods(itemToCook, nb);
				}
			}

			if (furnace.getStackInSlot(2) != null) {
				final Item item = furnace.getStackInSlot(2).getItem();
				final int meta = furnace.getStackInSlot(2).getItemDamage();

				dest.storeGoods(item, meta, furnace.getStackInSlot(2).stackSize);
				furnace.setInventorySlotContents(2, null);
			}
		}
		
		//Note : we treat all wood as identical, and turn them into oak planks
		//Would be more realistic to use the proper planks but would be trickier to handle 
		if (dest.countGoods(Blocks.log, -1) > 0) {
			if (furnace.getStackInSlot(1) == null) {
				final int nbplanks = Math.min(64, dest.countGoods(Blocks.log, -1) * 4);

				furnace.setInventorySlotContents(1, new ItemStack(Blocks.planks, nbplanks));
				
				//takes log2 also if available
				dest.takeGoods(Blocks.log, -1, nbplanks / 4);
			
			} else if (furnace.getStackInSlot(1).stackSize < 64) {

				final int nbplanks = Math.min(64 - furnace.getStackInSlot(1).stackSize, dest.countGoods(Blocks.log, -1) * 4);

				furnace.setInventorySlotContents(1, new ItemStack(Blocks.planks, furnace.getStackInSlot(1).stackSize + nbplanks));
				
				
				//takes log2 as well, if available
				dest.takeGoods(Blocks.log, -1, nbplanks / 4);				
			}
		}

		return true;
	}

}

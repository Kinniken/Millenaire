package org.millenaire.common.goal;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.millenaire.common.InvItem;
import org.millenaire.common.MLN;
import org.millenaire.common.MLN.MillenaireException;
import org.millenaire.common.MillVillager;
import org.millenaire.common.building.BuildingBlock;
import org.millenaire.common.building.BuildingPlan;
import org.millenaire.common.core.MillCommonUtilities;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;

public class GoalConstructionStepByStep extends Goal {

	@Override
	public int actionDuration(final MillVillager villager) {

		final BuildingBlock bblock = villager.getTownHall().getCurrentBlock();

		if (bblock == null) {
			return 0;
		}

		final int toolEfficiency = (int) villager.getBestShovel().getDigSpeed(new ItemStack(villager.getBestShovel(), 1), Blocks.dirt.getDefaultState());

		if (bblock.block == Blocks.air || bblock.block == Blocks.dirt) {
			return 100 - toolEfficiency * 5;
		}

		return 500 - toolEfficiency * 20;
	}

	@Override
	public GoalInformation getDestination(final MillVillager villager) {

		final BuildingBlock bblock = villager.getTownHall().getCurrentBlock();

		if (bblock == null) {
			return null;
		}

		return packDest(bblock.p);
	}

	@Override
	public ItemStack[] getHeldItemsTravelling(final MillVillager villager) {

		final BuildingBlock bblock = villager.getTownHall().getCurrentBlock();

		if (bblock != null) {
			if (bblock.block == Blocks.air || Item.getItemFromBlock(bblock.block) == null) {
				return villager.getBestShovelStack();
			} else {
				return new ItemStack[] { new ItemStack(bblock.block, 1, bblock.meta) };
			}
		} else {
			return villager.getBestShovelStack();
		}
	}

	@Override
	public AStarConfig getPathingConfig() {
		return JPS_CONFIG_BUILDING;
	}

	@Override
	public boolean isPossibleSpecific(final MillVillager villager) {
		if (!(villager.getTownHall().builder == null && villager.getTownHall().buildingLocationIP != null && villager.getTownHall().getBblocks() != null)) {
			return false;
		}

		for (final MillVillager v : villager.getTownHall().villagers) {
			if (Goal.getResourcesForBuild.key.equals(v.goalKey) || Goal.construction.key.equals(v.goalKey)) {
				return false;
			}
		}

		for (final InvItem key : villager.getTownHall().getCurrentBuildingPlan().resCost.keySet()) {
			if (villager.countInv(key) < villager.getTownHall().getCurrentBuildingPlan().resCost.get(key)) {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean isStillValidSpecific(final MillVillager villager) throws Exception {

		if (!(villager.getTownHall().builder == null) && villager.getTownHall().builder != villager) {
			return false;
		}
		return true;
	}

	@Override
	public boolean lookAtGoal() {
		return true;
	}

	@Override
	public void onAccept(final MillVillager villager) {
		villager.getTownHall().builder = villager;
	}

	@Override
	public boolean performAction(final MillVillager villager) throws MillenaireException {

		final BuildingBlock bblock = villager.getTownHall().getCurrentBlock();

		if (bblock == null) {
			return true;
		}

		if (MLN.LogWifeAI >= MLN.MINOR) {
			MLN.minor(villager, "Setting block at " + bblock.p + " type: " + bblock.block + " replacing: " + villager.getBlock(bblock.p) + " distance: " + bblock.p.distanceTo(villager));
		}

		if (bblock.p.horizontalDistanceTo(villager) < 1 && bblock.p.getiY() > villager.posY && bblock.p.getiY() < villager.posY + 2) {
			boolean jumped = false;
			final World world = villager.worldObj;
			if (!MillCommonUtilities.isBlockOpaqueCube(world, villager.getPos().getiX() + 1, villager.getPos().getiY() + 1, villager.getPos().getiZ())
					&& !MillCommonUtilities.isBlockOpaqueCube(world, villager.getPos().getiX() + 1, villager.getPos().getiY() + 2, villager.getPos().getiZ())) {

				villager.setPosition(villager.getPos().getiX() + 1, villager.getPos().getiY() + 1, villager.getPos().getiZ());
				jumped = true;
			}
			if (!jumped && !MillCommonUtilities.isBlockOpaqueCube(world, villager.getPos().getiX() - 1, villager.getPos().getiY() + 1, villager.getPos().getiZ())
					&& !MillCommonUtilities.isBlockOpaqueCube(world, villager.getPos().getiX() - 1, villager.getPos().getiY() + 2, villager.getPos().getiZ())) {

				villager.setPosition(villager.getPos().getiX() - 1, villager.getPos().getiY() + 1, villager.getPos().getiZ());
				jumped = true;
			}
			if (!jumped && !MillCommonUtilities.isBlockOpaqueCube(world, villager.getPos().getiX(), villager.getPos().getiY(), villager.getPos().getiZ() + 1)
					&& !MillCommonUtilities.isBlockOpaqueCube(world, villager.getPos().getiX(), villager.getPos().getiY() + 2, villager.getPos().getiZ() + 1)) {

				villager.setPosition(villager.getPos().getiX(), villager.getPos().getiY() + 1, villager.getPos().getiZ() + 1);
				jumped = true;
			}
			if (!jumped && !MillCommonUtilities.isBlockOpaqueCube(world, villager.getPos().getiX(), villager.getPos().getiY() + 1, villager.getPos().getiZ() - 1)
					&& !MillCommonUtilities.isBlockOpaqueCube(world, villager.getPos().getiX(), villager.getPos().getiY() + 2, villager.getPos().getiZ() - 1)) {

				villager.setPosition(villager.getPos().getiX(), villager.getPos().getiY() + 1, villager.getPos().getiZ() - 1);
				jumped = true;
			}
			if (!jumped && MLN.LogWifeAI >= MLN.MAJOR) {
				MLN.major(villager, "Tried jumping in construction but couldn't");
			}
		}

		bblock.build(villager.worldObj, false, false);

		villager.swingItem();

		boolean foundNextBlock = false;

		while (!foundNextBlock && villager.getTownHall().areBlocksLeft()) {
			villager.getTownHall().incrementBblockPos();

			final BuildingBlock bb = villager.getTownHall().getCurrentBlock();
			if (bb != null && !bb.alreadyDone(villager.worldObj)) {
				villager.setGoalDestPoint(bb.p);
				foundNextBlock = true;
			}
		}

		if (!villager.getTownHall().areBlocksLeft()) {
			if (MLN.LogBuildingPlan >= MLN.MAJOR) {
				MLN.major(this, "Villager " + villager + " laid last block in " + villager.getTownHall().buildingLocationIP.planKey + " at " + bblock.p);
			}
			villager.getTownHall().setBblocks(null);
			final BuildingPlan plan = villager.getTownHall().getCurrentBuildingPlan();

			for (final InvItem key : plan.resCost.keySet()) {
				villager.takeFromInv(key.getItem(), key.meta, plan.resCost.get(key));
			}

			if (villager.getTownHall().buildingLocationIP != null && villager.getTownHall().buildingLocationIP.level == 0) {
				villager.getTownHall().initialiseCurrentConstruction(bblock.p);
			}
		}

		if (!foundNextBlock) {
			villager.setGoalDestPoint(null);
		}

		if (MLN.LogWifeAI >= MLN.MINOR && villager.extraLog) {
			MLN.minor(villager, "Reseting actionStart after " + (System.currentTimeMillis() - villager.actionStart));
		}

		villager.actionStart = 0;

		return !villager.getTownHall().areBlocksLeft();
	}

	@Override
	public int priority(final MillVillager villager) {
		return 150;
	}

	@Override
	public int range(final MillVillager villager) {
		return ACTIVATION_RANGE + 2;
	}

	@Override
	public boolean stopMovingWhileWorking() {
		return false;
	}

	@Override
	public boolean stuckAction(final MillVillager villager) throws MillenaireException {
		if (villager.getGoalDestPoint().distanceTo(villager) < 30) {
			if (MLN.LogWifeAI >= MLN.MINOR) {
				MLN.major(villager, "Putting block at a distance: " + villager.getGoalDestPoint().distanceTo(villager));
			}
			performAction(villager);

			return true;
		}
		return false;
	}

	@Override
	public boolean unreachableDestination(final MillVillager villager) throws Exception {

		performAction(villager);

		return true;
	}
}

package org.millenaire.common.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.millenaire.common.MLN;
import org.millenaire.common.core.MillCommonUtilities;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.Goods.ItemText;

public class ItemPurse extends ItemText {

	private static final String ML_PURSE_DENIER = "ml_Purse_denier";
	private static final String ML_PURSE_DENIERARGENT = "ml_Purse_denierargent";
	private static final String ML_PURSE_DENIEROR = "ml_Purse_denieror";
	private static final String ML_PURSE_RAND = "ml_Purse_rand";

	public ItemPurse(final String iconName) {
		super(iconName);
	}

	@Override
	public ItemStack onItemRightClick(final ItemStack purse, final World world, final EntityPlayer player) {

		if (totalDeniers(purse) > 0) {
			removeDeniersFromPurse(purse, player);
		} else {
			storeDeniersInPurse(purse, player);
		}

		return super.onItemRightClick(purse, world, player);
	}

	private void removeDeniersFromPurse(final ItemStack purse, final EntityPlayer player) {
		if (purse.getTagCompound() != null) {
			final int deniers = purse.getTagCompound().getInteger(ML_PURSE_DENIER);
			final int denierargent = purse.getTagCompound().getInteger(ML_PURSE_DENIERARGENT);
			final int denieror = purse.getTagCompound().getInteger(ML_PURSE_DENIEROR);

			int result = MillCommonUtilities.putItemsInChest(player.inventory, Mill.denier, deniers);
			purse.getTagCompound().setInteger(ML_PURSE_DENIER, deniers - result);

			result = MillCommonUtilities.putItemsInChest(player.inventory, Mill.denier_argent, denierargent);
			purse.getTagCompound().setInteger(ML_PURSE_DENIERARGENT, denierargent - result);

			result = MillCommonUtilities.putItemsInChest(player.inventory, Mill.denier_or, denieror);
			purse.getTagCompound().setInteger(ML_PURSE_DENIEROR, denieror - result);

			purse.getTagCompound().setInteger(ML_PURSE_RAND, player.worldObj.isRemote ? 0 : 1);

			setItemName(purse);
		}
	}

	public void setDeniers(final ItemStack purse, final EntityPlayer player, final int amount) {

		final int denier = amount % 64;
		final int denier_argent = (amount - denier) / 64 % 64;
		final int denier_or = (amount - denier - denier_argent * 64) / (64 * 64);

		setDeniers(purse, player, denier, denier_argent, denier_or);

	}

	public void setDeniers(final ItemStack purse, final EntityPlayer player, final int denier, final int denierargent, final int denieror) {
		if (purse.getTagCompound() == null) {
			purse.setTagCompound(new NBTTagCompound());
		}

		purse.getTagCompound().setInteger(ML_PURSE_DENIER, denier);
		purse.getTagCompound().setInteger(ML_PURSE_DENIERARGENT, denierargent);
		purse.getTagCompound().setInteger(ML_PURSE_DENIEROR, denieror);

		purse.getTagCompound().setInteger(ML_PURSE_RAND, player.worldObj.isRemote ? 0 : 1);

		setItemName(purse);
	}

	private void setItemName(final ItemStack purse) {
		if (purse.getTagCompound() == null) {
			purse.setStackDisplayName(MLN.string("item.purse"));
		} else {
			final int deniers = purse.getTagCompound().getInteger(ML_PURSE_DENIER);
			final int denierargent = purse.getTagCompound().getInteger(ML_PURSE_DENIERARGENT);
			final int denieror = purse.getTagCompound().getInteger(ML_PURSE_DENIEROR);

			String label = "";

			if (denieror != 0) {
				label = "\247" + MLN.YELLOW + denieror + "o ";
			}
			if (denierargent != 0) {
				label += "\247" + MLN.WHITE + denierargent + "a ";
			}
			if (deniers != 0 || label.length() == 0) {
				label += "\247" + MLN.ORANGE + deniers + "d";
			}

			label.trim();

			purse.setStackDisplayName("\247" + MLN.WHITE + MLN.string("item.purse") + ": " + label);
		}

	}

	private void storeDeniersInPurse(final ItemStack purse, final EntityPlayer player) {

		final int deniers = MillCommonUtilities.getItemsFromChest(player.inventory, Mill.denier, 0, Integer.MAX_VALUE);
		final int denierargent = MillCommonUtilities.getItemsFromChest(player.inventory, Mill.denier_argent, 0, Integer.MAX_VALUE);
		final int denieror = MillCommonUtilities.getItemsFromChest(player.inventory, Mill.denier_or, 0, Integer.MAX_VALUE);

		final int total = totalDeniers(purse) + deniers + denierargent * 64 + denieror * 64 * 64;

		final int new_denier = total % 64;
		final int new_denier_argent = (total - new_denier) / 64 % 64;
		final int new_denier_or = (total - new_denier - new_denier_argent * 64) / (64 * 64);

		setDeniers(purse, player, new_denier, new_denier_argent, new_denier_or);
	}

	public int totalDeniers(final ItemStack purse) {
		if (purse.getTagCompound() == null) {
			return 0;
		}

		final int deniers = purse.getTagCompound().getInteger(ML_PURSE_DENIER);
		final int denierargent = purse.getTagCompound().getInteger(ML_PURSE_DENIERARGENT);
		final int denieror = purse.getTagCompound().getInteger(ML_PURSE_DENIEROR);

		return deniers + denierargent * 64 + denieror * 64 * 64;
	}

}

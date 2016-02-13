// Decompiled by Jad v1.5.8g. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   ItemMillSeeds.java

package org.millenaire.common.item;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.EnumPlantType;
import net.minecraftforge.common.IPlantable;

import org.millenaire.common.MLN;
import org.millenaire.common.Point;
import org.millenaire.common.UserProfile;
import org.millenaire.common.core.MillCommonUtilities;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.forge.MillAchievements;
import org.millenaire.common.network.ServerSender;

// Referenced classes of package org.millenaire.common.item: Goods

public class ItemMillSeeds extends Goods.ItemText implements IPlantable {

	public final Block crop;

	public final String cropKey;

	public ItemMillSeeds(final String iconName, final Block j, final String cropKey) {
		super(iconName);
		crop = j;
		this.cropKey = cropKey;
		setCreativeTab(Mill.tabMillenaire);
	}

	@Override
	public Block getPlant(final IBlockAccess world, BlockPos pos) {
		return crop;
	} 

	@Override
	public int getPlantMetadata(final IBlockAccess world, BlockPos pos) {
		return 0;
	}

	@Override
	public EnumPlantType getPlantType(final IBlockAccess world, BlockPos pos) {
		return EnumPlantType.Crop;
	}

	@Override
	public boolean onItemUse(final ItemStack itemstack, final EntityPlayer entityplayer, final World world, BlockPos pos, EnumFacing side, final float hitX, final float hitY,
			final float hitZ) {
		if (side != EnumFacing.DOWN) {
			return false;
		}
		if (!entityplayer.canPlayerEdit(pos, side, itemstack) || !entityplayer.canPlayerEdit(pos.up(), side, itemstack)) {
			return false;
		}
		final UserProfile profile = Mill.getMillWorld(world).getProfile(entityplayer.getName());
		if (!profile.isTagSet(new StringBuilder().append("cropplanting_").append(cropKey).toString()) && !MLN.DEV) {
			if (!world.isRemote) {
				ServerSender.sendTranslatedSentence(entityplayer, 'f', "ui.cropplantingknowledge", new String[] { new StringBuilder().append("item.").append(cropKey).toString() });
			}
			return false;
		}
		
		
		final IBlockState state = world.getBlockState(pos);
		if (state.getBlock() == Blocks.farmland && world.getBlockState(pos.up()).getBlock()!=Blocks.air) {
			MillCommonUtilities.setBlockAndMetadata(world, new Point(pos.up()), crop, 0, true, false);
			itemstack.stackSize--;
			if (!world.isRemote) {
				entityplayer.addStat(MillAchievements.masterfarmer, 1);
			}
			return true;
		} else {
			return false;
		}
	}
}

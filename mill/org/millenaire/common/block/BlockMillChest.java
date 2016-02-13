package org.millenaire.common.block;

import java.util.Iterator;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.ILockableContainer;
import net.minecraft.world.World;

import org.millenaire.client.network.ClientSender;
import org.millenaire.common.Point;
import org.millenaire.common.TileEntityMillChest;
import org.millenaire.common.TileEntityMillChest.InventoryMillLargeChest;
import org.millenaire.common.forge.Mill;

public class BlockMillChest extends BlockChest {

	public static ContainerChest createContainer(final World world, final int i, final int j, final int k, final EntityPlayer entityplayer) {
		final TileEntityMillChest lockedchest = (TileEntityMillChest) world.getTileEntity(new BlockPos(i,j,k));

		final IInventory chest = getInventory(lockedchest, world, i, j, k);

		return new ContainerChest(entityplayer.inventory, chest, entityplayer);
	}

	// Copied from BlockChest
	private static boolean func_149953_o(final World p_149953_0_, final int p_149953_1_, final int p_149953_2_, final int p_149953_3_) {
		final Iterator iterator = p_149953_0_.getEntitiesWithinAABB(EntityOcelot.class,
				AxisAlignedBB.fromBounds(p_149953_1_, p_149953_2_ + 1, p_149953_3_, p_149953_1_ + 1, p_149953_2_ + 2, p_149953_3_ + 1)).iterator();
		EntityOcelot entityocelot;

		do {
			if (!iterator.hasNext()) {
				return false;
			}

			final Entity entity = (Entity) iterator.next();
			entityocelot = (EntityOcelot) entity;
		} while (!entityocelot.isSitting());

		return true;
	}

	public static IInventory getInventory(final TileEntityMillChest lockedchest, final World world, final int i, final int j, final int k) {

		final String largename = lockedchest.getInvLargeName();

		ILockableContainer chest = lockedchest;

		final Block block = world.getBlockState(new BlockPos(i, j, k)).getBlock();

		if (world.getBlockState(new BlockPos(i-1, j, k)).getBlock() == block) {
			chest = new InventoryLargeChest(largename, (TileEntityChest) world.getTileEntity(new BlockPos(i-1, j, k)), chest);
		}
		if (world.getBlockState(new BlockPos(i+1, j, k)).getBlock() == block) {
			chest = new InventoryLargeChest(largename, chest, (TileEntityChest) world.getTileEntity(new BlockPos(i+1, j, k)));
		}
		if (world.getBlockState(new BlockPos(i, j, k-1)).getBlock() == block) {
			chest = new InventoryLargeChest(largename, (TileEntityChest) world.getTileEntity(new BlockPos(i, j, k-1)), chest);
		}
		if (world.getBlockState(new BlockPos(i, j, k+1)).getBlock() == block) {
			chest = new InventoryLargeChest(largename, chest, (TileEntityChest) world.getTileEntity(new BlockPos(i, j, k+1)));
		}

		return chest;
	}

	public BlockMillChest() {
		super(2);
	}

	public BlockMillChest(final int blockID) {
		super(0);
		this.setCreativeTab(Mill.tabMillenaire);
	}


	@Override
	public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
		
	}

	@Override
	public TileEntity createNewTileEntity(final World world, final int p_149915_2_) {
		return new TileEntityMillChest();
	}

	@Override
	public TileEntity createTileEntity(final World world, IBlockState state) {
		return new TileEntityMillChest();
	}

	
	
	  private boolean isBlocked(World worldIn, BlockPos pos)
	    {
	        return this.isBelowSolidBlock(worldIn, pos) || this.isOcelotSittingOnChest(worldIn, pos);
	    }

	    private boolean isBelowSolidBlock(World worldIn, BlockPos pos)
	    {
	        return worldIn.isSideSolid(pos.up(), EnumFacing.DOWN, false);
	    }

	    private boolean isOcelotSittingOnChest(World worldIn, BlockPos pos)
	    {
	        @SuppressWarnings("rawtypes")
			Iterator iterator = worldIn.getEntitiesWithinAABB(EntityOcelot.class, new AxisAlignedBB((double)pos.getX(), (double)(pos.getY() + 1), (double)pos.getZ(), (double)(pos.getX() + 1), (double)(pos.getY() + 2), (double)(pos.getZ() + 1))).iterator();
	        EntityOcelot entityocelot;

	        do
	        {
	            if (!iterator.hasNext())
	            {
	                return false;
	            }

	            Entity entity = (Entity)iterator.next();
	            entityocelot = (EntityOcelot)entity;
	        }
	        while (!entityocelot.isSitting());

	        return true;
	    }
	
	
	@Override
	public ILockableContainer getLockableContainer(World worldIn, BlockPos pos)
    {
        TileEntity tileentity = worldIn.getTileEntity(pos);

        if (!(tileentity instanceof TileEntityMillChest))
        {
            return null;
        }
        else
        {
        	ILockableContainer millChest = (TileEntityMillChest)tileentity;

            if (this.isBlocked(worldIn, pos))
            {
                return null;
            }
            else
            {
                @SuppressWarnings("unchecked")
				Iterator<EnumFacing> iterator = EnumFacing.Plane.HORIZONTAL.iterator();

                while (iterator.hasNext())
                {
                    EnumFacing enumfacing = (EnumFacing)iterator.next();
                    BlockPos blockpos1 = pos.offset(enumfacing);
                    Block block = worldIn.getBlockState(blockpos1).getBlock();

                    if (block == this)
                    {
                        if (this.isBlocked(worldIn, blockpos1))
                        {
                            return null;
                        }

                        TileEntity tileentity1 = worldIn.getTileEntity(blockpos1);

                        if (tileentity1 instanceof TileEntityMillChest)
                        {
                            if (enumfacing != EnumFacing.WEST && enumfacing != EnumFacing.NORTH)
                            {
                            	millChest = new InventoryMillLargeChest("container.chestDouble", (ILockableContainer)millChest, (TileEntityMillChest)tileentity1);
                            }
                            else
                            {
                            	millChest = new InventoryMillLargeChest("container.chestDouble", (TileEntityMillChest)tileentity1, (ILockableContainer)millChest);
                            }
                        }
                    }
                }

                return (ILockableContainer)millChest;
            }
        }
    }

	@Override
	public boolean onBlockActivated(final World world, BlockPos pos, IBlockState state, final EntityPlayer entityplayer, EnumFacing side, final float par7, final float par8, final float par9) {
		if (world.isRemote) {
			ClientSender.activateMillChest(entityplayer, new Point(pos));
		}
		return true;
	}

	@Override
	public void onBlockClicked(World worldIn, BlockPos pos,
			EntityPlayer playerIn) {
		if (playerIn.inventory.getCurrentItem() != null && playerIn.inventory.getCurrentItem().getItem() != Mill.summoningWand) {
			super.onBlockClicked(worldIn, pos, playerIn);
		}
	}

	@Override
	public int quantityDropped(final Random random) {
		return 0;
	}

}

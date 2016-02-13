package org.millenaire.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.millenaire.common.MLN;
import org.millenaire.common.TileEntityMillChest;

public class TileEntityMillChestRenderer extends TileEntitySpecialRenderer {
	
	private ModelLockedChest simpleChest = new ModelLockedChest();
    private ModelLargeLockedChest largeChest = new ModelLargeLockedChest();
	
	public class ModelLargeLockedChest extends ModelLockedChest {
		public ModelLargeLockedChest() {
			chestLid = new ModelRenderer(this, 0, 0).setTextureSize(128, 64);
			chestLid.addBox(0.0F, -5F, -14F, 30, 5, 14, 0.0F);
			chestLid.rotationPointX = 1.0F;
			chestLid.rotationPointY = 7F;
			chestLid.rotationPointZ = 15F;
			chestKnob = new ModelRenderer(this, 0, 0).setTextureSize(128, 64);
			chestKnob.addBox(-2F, -2F, -15F, 4, 4, 1, 0.0F);
			chestKnob.rotationPointX = 16F;
			chestKnob.rotationPointY = 7F;
			chestKnob.rotationPointZ = 15F;
			chestBelow = new ModelRenderer(this, 0, 19).setTextureSize(128, 64);
			chestBelow.addBox(0.0F, 0.0F, 0.0F, 30, 10, 14, 0.0F);
			chestBelow.rotationPointX = 1.0F;
			chestBelow.rotationPointY = 6F;
			chestBelow.rotationPointZ = 1.0F;
		}
	}

	private class ModelLockedChest extends ModelBase {
		public ModelRenderer chestLid;
		public ModelRenderer chestBelow;
		public ModelRenderer chestKnob;

		public ModelLockedChest() {
			chestLid = new ModelRenderer(this, 0, 0).setTextureSize(64, 64);
			chestLid.addBox(0.0F, -5F, -14F, 14, 5, 14, 0.0F);
			chestLid.rotationPointX = 1.0F;
			chestLid.rotationPointY = 7F;
			chestLid.rotationPointZ = 15F;
			chestKnob = new ModelRenderer(this, 0, 0).setTextureSize(64, 64);
			chestKnob.addBox(-2F, -2F, -15F, 4, 4, 1, 0.0F);
			chestKnob.rotationPointX = 8F;
			chestKnob.rotationPointY = 7F;
			chestKnob.rotationPointZ = 15F;
			chestBelow = new ModelRenderer(this, 0, 19).setTextureSize(64, 64);
			chestBelow.addBox(0.0F, 0.0F, 0.0F, 14, 10, 14, 0.0F);
			chestBelow.rotationPointX = 1.0F;
			chestBelow.rotationPointY = 6F;
			chestBelow.rotationPointZ = 1.0F;
		}

		public void renderAll() {
			chestKnob.rotateAngleX = chestLid.rotateAngleX;
			chestLid.render(0.0625F);
			chestKnob.render(0.0625F);
			chestBelow.render(0.0625F);
		}
	}

	public TileEntityMillChestRenderer() {

	}

	@Override
	public void renderTileEntityAt(TileEntity p_180535_1_, double posX, double posZ, double p_180535_6_, float p_180535_8_, int p_180535_9_)
    {
        this.renderTileEntityChestAt((TileEntityMillChest)p_180535_1_, posX, posZ, p_180535_6_, p_180535_8_, p_180535_9_);
    }

	public void renderTileEntityChestAt(TileEntityMillChest p_180538_1_, double p_180538_2_, double p_180538_4_, double p_180538_6_, float p_180538_8_, int p_180538_9_)
    {
        int j;

        if (!p_180538_1_.hasWorldObj())
        {
            j = 0;
        }
        else
        {
            Block block = p_180538_1_.getBlockType();
            j = p_180538_1_.getBlockMetadata();

            if (block instanceof BlockChest && j == 0)
            {
                ((BlockChest)block).checkForSurroundingChests(p_180538_1_.getWorld(), p_180538_1_.getPos(), p_180538_1_.getWorld().getBlockState(p_180538_1_.getPos()));
                j = p_180538_1_.getBlockMetadata();
            }

            p_180538_1_.checkForAdjacentChests();
        }

        if (p_180538_1_.adjacentChestZNeg == null && p_180538_1_.adjacentChestXNeg == null)
        {
            ModelLockedChest modelchest;

            if (p_180538_1_.adjacentChestXPos == null && p_180538_1_.adjacentChestZPos == null)
            {
                modelchest = this.simpleChest;

                if (p_180538_9_ >= 0)
                {
                    this.bindTexture(DESTROY_STAGES[p_180538_9_]);
                    GlStateManager.matrixMode(5890);
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(4.0F, 4.0F, 1.0F);
                    GlStateManager.translate(0.0625F, 0.0625F, 0.0625F);
                    GlStateManager.matrixMode(5888);
                }
                else
                {
                    this.bindTexture(MLN.getLockedChestTexture());
                }
            }
            else
            {
                modelchest = this.largeChest;

                if (p_180538_9_ >= 0)
                {
                    this.bindTexture(DESTROY_STAGES[p_180538_9_]);
                    GlStateManager.matrixMode(5890);
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(8.0F, 4.0F, 1.0F);
                    GlStateManager.translate(0.0625F, 0.0625F, 0.0625F);
                    GlStateManager.matrixMode(5888);
                }
                else
                {
                    this.bindTexture(MLN.getLargeLockedChestTexture());
                }
            }

            GlStateManager.pushMatrix();
            GlStateManager.enableRescaleNormal();

            if (p_180538_9_ < 0)
            {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }

            GlStateManager.translate((float)p_180538_2_, (float)p_180538_4_ + 1.0F, (float)p_180538_6_ + 1.0F);
            GlStateManager.scale(1.0F, -1.0F, -1.0F);
            GlStateManager.translate(0.5F, 0.5F, 0.5F);
            short short1 = 0;

            if (j == 2)
            {
                short1 = 180;
            }

            if (j == 3)
            {
                short1 = 0;
            }

            if (j == 4)
            {
                short1 = 90;
            }

            if (j == 5)
            {
                short1 = -90;
            }

            if (j == 2 && p_180538_1_.adjacentChestXPos != null)
            {
                GlStateManager.translate(1.0F, 0.0F, 0.0F);
            }

            if (j == 5 && p_180538_1_.adjacentChestZPos != null)
            {
                GlStateManager.translate(0.0F, 0.0F, -1.0F);
            }

            GlStateManager.rotate((float)short1, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(-0.5F, -0.5F, -0.5F);
            float f1 = p_180538_1_.prevLidAngle + (p_180538_1_.lidAngle - p_180538_1_.prevLidAngle) * p_180538_8_;
            float f2;

            if (p_180538_1_.adjacentChestZNeg != null)
            {
                f2 = p_180538_1_.adjacentChestZNeg.prevLidAngle + (p_180538_1_.adjacentChestZNeg.lidAngle - p_180538_1_.adjacentChestZNeg.prevLidAngle) * p_180538_8_;

                if (f2 > f1)
                {
                    f1 = f2;
                }
            }

            if (p_180538_1_.adjacentChestXNeg != null)
            {
                f2 = p_180538_1_.adjacentChestXNeg.prevLidAngle + (p_180538_1_.adjacentChestXNeg.lidAngle - p_180538_1_.adjacentChestXNeg.prevLidAngle) * p_180538_8_;

                if (f2 > f1)
                {
                    f1 = f2;
                }
            }

            f1 = 1.0F - f1;
            f1 = 1.0F - f1 * f1 * f1;
            modelchest.chestLid.rotateAngleX = -(f1 * (float)Math.PI / 2.0F);
            modelchest.renderAll();
            GlStateManager.disableRescaleNormal();
            GlStateManager.popMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            if (p_180538_9_ >= 0)
            {
                GlStateManager.matrixMode(5890);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(5888);
            }
        }
    }
	
}

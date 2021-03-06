package nallar.patched.entity;

import nallar.tickthreading.patcher.Declare;
import nallar.tickthreading.util.concurrent.SimpleMutex;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.item.ItemExpireEvent;

public abstract class PatchEntityItem extends EntityItem {
	private static final SimpleMutex lock = new SimpleMutex();
	private static final double mergeRadius = 1.5D;
	private static final double aggressiveMergeRadius = 5D;
	private static final float aggressiveMergeRadiusSquared = 25f;
	private static EntityItem last;

	public PatchEntityItem(World par1World, double par2, double par4, double par6) {
		super(par1World, par2, par4, par6);
	}

	@Override
	public void onUpdate() {
		if (this.delayBeforeCanPickup > 0) {
			--this.delayBeforeCanPickup;
		}

		boolean forceUpdate = this.ticksExisted % 75 == 1;
		this.prevPosX = this.posX;
		this.prevPosY = this.posY;
		this.prevPosZ = this.posZ;
		this.motionY -= 0.03999999910593033D;
		if (forceUpdate || noClip) {
			this.noClip = this.pushOutOfBlocks(this.posX, (this.boundingBox.minY + this.boundingBox.maxY) / 2.0D, this.posZ);
		}
		this.moveEntity(this.motionX, this.motionY, this.motionZ);
		boolean var1 = (int) this.prevPosX != (int) this.posX || (int) this.prevPosY != (int) this.posY || (int) this.prevPosZ != (int) this.posZ;

		if ((var1 && this.ticksExisted % 5 == 0) || forceUpdate) {
			if (this.worldObj.getBlockMaterial(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.posY), MathHelper.floor_double(this.posZ)) == Material.lava) {
				this.motionY = 0.20000000298023224D;
				this.motionX = (double) ((this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F);
				this.motionZ = (double) ((this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F);
				this.playSound("random.fizz", 0.4F, 2.0F + this.rand.nextFloat() * 0.4F);
			}

			if (forceUpdate && !this.worldObj.isRemote) {
				this.searchForOtherItemsNearby();
			}
		}

		float var2 = 0.98F;

		if (this.onGround) {
			var2 = 0.58800006F;
			if (this.motionX > 0.0001 || this.motionY > 0.0001 || this.motionZ > 0.0001) {
				int var3 = this.worldObj.getBlockIdWithoutLoad(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.boundingBox.minY) - 1, MathHelper.floor_double(this.posZ));

				if (var3 > 0) {
					var2 = Block.blocksList[var3].slipperiness * 0.98F;
				}
			}
		}

		this.motionX *= (double) var2;
		this.motionY *= 0.9800000190734863D;
		this.motionZ *= (double) var2;

		if (this.onGround) {
			this.motionY *= -0.5D;
		}

		++this.age;

		ItemStack item = getDataWatcher().getWatchableObjectItemStack(10);

		if (!this.worldObj.isRemote && this.age >= lifespan) {
			if (item != null) {
				ItemExpireEvent event = new ItemExpireEvent(this, (item.getItem() == null ? 6000 : item.getItem().getEntityLifespan(item, worldObj)));
				if (MinecraftForge.EVENT_BUS.post(event)) {
					lifespan += event.extraLife;
				} else {
					this.setDead();
				}
			} else {
				this.setDead();
			}
		}

		if (item != null && item.stackSize <= 0) {
			this.setDead();
		}
	}

	@Declare
	public static void resetLast() {
		last = null;
	}

	@Override
	@Declare
	public boolean aggressiveCombine() {
		EntityItem last_ = last;
		if (last_ != null && this.getDistanceToEntitySq(last_) < aggressiveMergeRadiusSquared && last_.combineItems(this)) {
			return true;
		}
		for (EntityItem entityItem : (Iterable<EntityItem>) this.worldObj.selectEntitiesWithinAABB(EntityItem.class, this.boundingBox.expand(aggressiveMergeRadius, aggressiveMergeRadius, aggressiveMergeRadius), null, 1D)) {
			if (entityItem != last_ && entityItem.combineItems(this)) {
				return true;
			}
		}
		last = this;
		return false;
	}

	@Override
	protected void searchForOtherItemsNearby() {
		for (EntityItem entityItem : (Iterable<EntityItem>) this.worldObj.selectEntitiesWithinAABB(EntityItem.class, this.boundingBox.expand(mergeRadius, mergeRadius, mergeRadius), null, 1D)) {
			if (this.combineItems(entityItem)) {
				return;
			}
		}
	}

	@Override
	public boolean combineItems(EntityItem other) {
		if (other == this) {
			return false;
		}
		lock.lock();
		synchronized (this) {
			synchronized (other) {
				lock.unlock();
				if (this.isDead || other.isDead) {
					return false;
				}
				ItemStack thisStack = this.getEntityItem();
				ItemStack otherStack = other.getEntityItem();

				if (thisStack.getItem() != otherStack.getItem()) {
					return false;
				} else if (thisStack.hasTagCompound() ^ otherStack.hasTagCompound()) {
					return false;
				} else if (thisStack.hasTagCompound() && !thisStack.getTagCompound().equals(otherStack.getTagCompound())) {
					return false;
				} else if (thisStack.getItem().getHasSubtypes() && thisStack.getItemDamage() != otherStack.getItemDamage()) {
					return false;
				} else if (thisStack.stackSize + otherStack.stackSize > thisStack.getMaxStackSize()) {
					return false;
				} else {
					thisStack.stackSize += otherStack.stackSize;
					otherStack.stackSize = 0;
					this.delayBeforeCanPickup = Math.max(other.delayBeforeCanPickup, this.delayBeforeCanPickup);
					this.age = Math.min(other.age, this.age);
					this.setEntityItemStack(thisStack);
					other.setDead();
					return true;
				}
			}
		}
	}

	@Override
	@Declare
	public void combineList(java.util.ArrayList<EntityItem> list) {
		if (this.isDead) {
			return;
		}
		for (EntityItem entityItem : list) {
			if (!entityItem.isDead) {
				entityItem.combineItems(this);
			}
			// May be set true in combineItems.
			//noinspection ConstantConditions
			if (this.isDead) {
				return;
			}
		}
	}
}

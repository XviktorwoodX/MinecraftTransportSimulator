package minecraftflightsimulator.entities.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import minecraftflightsimulator.MFSRegistry;
import minecraftflightsimulator.entities.parts.EntityEngine;
import minecraftflightsimulator.entities.parts.EntityEngineAircraft;
import minecraftflightsimulator.entities.parts.EntityPontoon;
import minecraftflightsimulator.entities.parts.EntityPontoonDummy;
import minecraftflightsimulator.entities.parts.EntityPropeller;
import minecraftflightsimulator.entities.parts.EntitySkid;
import minecraftflightsimulator.entities.parts.EntityWheel;
import minecraftflightsimulator.entities.parts.EntityWheelLarge;
import minecraftflightsimulator.entities.parts.EntityWheelSmall;
import minecraftflightsimulator.items.ItemEngine;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * For this class the following slot rules apply:
 * Slot 1 is for the center wheel on aircraft.
 * Slots 2 and 3 are for left wheels, while slot 4 and 5 are for right wheels.
 * Slots 6-9 are for engines.
 * Slots 10-13 are for propellers.
 */
public abstract class EntityFlyable extends EntityVehicle{
	/**
	 * Map that contains landing gear mappings.  Keyed by landing gear's UUID.
	 * Note that this is for performing landing operations, and will update as landing gears are
	 * linked and destroyed.
	 */
	private Map<String, EntityLandingGear> landingGears = new HashMap<String, EntityLandingGear>();
	
	/**
	 * Map that contains engine mappings.  Keyed by engine's UUID.
	 * Note that this is for performing engine operations, and will update as engines are
	 * linked and destroyed.
	 */
	private Map<String, EntityEngine> engines = new HashMap<String, EntityEngine>();
	
	/**
	 * Map that contains propeller mappings.  Keyed by propeller's UUID.
	 * Note that this is for performing propeller operations, and will update as propeller are
	 * linked and destroyed.
	 */
	private Map<String, EntityPropeller> propellers = new HashMap<String, EntityPropeller>();
	
	/**
	 * BiMap that contains mappings of engine UUID to propeller UUID.  Used when propellers
	 * need to know what engine powers them.  Or when engines need to know what propeller to power.
	 */
	private BiMap<String, String> enginePropellers = HashBiMap.create();
	
	public EntityFlyable(World world){
		super(world);
	}
	
	public EntityFlyable(World world, float posX, float posY, float posZ, float playerRotation){
		super(world, posX, posY, posZ, playerRotation);
	}
	
	@Override
	protected void entityInit(){
		super.entityInit();
		initPlaneProperties();
	}
	
	protected void addCenterGearPosition(float[] coords){
		if(!partPositions.containsKey(1)){
			partPositions.put(1, coords);
		}else{
			System.err.println("AN ENTITY HAS TRIED TO ADD TOO MANY CENTER GEARS!  THINGS MAY GO BADLY!");
		}
	}
	
	protected void addLeftGearPosition(float[] coords){
		if(!partPositions.containsKey(2)){
			partPositions.put(2, coords);
		}else if(!partPositions.containsKey(3)){
			partPositions.put(3, coords);
		}else{
			System.err.println("AN ENTITY HAS TRIED TO ADD TOO MANY LEFT GEARS!  THINGS MAY GO BADLY!");
		}
	}
	
	protected void addRightGearPosition(float[] coords){
		if(!partPositions.containsKey(4)){
			partPositions.put(4, coords);
		}else if(!partPositions.containsKey(5)){
			partPositions.put(5, coords);
		}else{
			System.err.println("AN ENTITY HAS TRIED TO ADD TOO MANY RIGHT GEARS!  THINGS MAY GO BADLY!");
		}
	}
	
	protected void addEnginePosition(float[] coords){
		for(int i=6; i<=9; ++i){
			if(!partPositions.containsKey(i)){
				partPositions.put(i, coords);
				return;
			}
		}
		System.err.println("AN ENTITY HAS TRIED TO ADD TOO MANY ENGINES!  THINGS MAY GO BADLY!");
	}
	
	protected void addPropellerPosition(float[] coords){
		for(int i=10; i<=13; ++i){
			if(!partPositions.containsKey(i)){
				partPositions.put(i, coords);
				return;
			}
		}
		System.err.println("AN ENTITY HAS TRIED TO ADD TOO MANY PROPELLERS!  THINGS MAY GO BADLY!");
	}
	
	
	@Override
	public void addChild(String childUUID, EntityChild child, boolean newChild){
		super.addChild(childUUID, child, newChild);
		if(child instanceof EntityLandingGear){
			landingGears.put(childUUID, (EntityLandingGear) child);
		}else if(child instanceof EntityEngine){
			engines.put(childUUID, (EntityEngine) child);
			float[] childOffset = new float[]{child.offsetX, child.offsetY, child.offsetZ};
			for(int i=1; i<controllerSlot; ++i){
				if(Arrays.equals(childOffset, partPositions.get(i))){
					if(getChildAtLocation(partPositions.get(i+4)) != null){
						enginePropellers.forcePut(childUUID, getChildAtLocation(partPositions.get(i+4)).UUID);
					}
				}
			}
		}else if(child instanceof EntityPropeller){
			propellers.put(childUUID, (EntityPropeller) child);
			float[] childOffset = new float[]{child.offsetX, child.offsetY, child.offsetZ};
			for(int i=1; i<controllerSlot; ++i){
				if(Arrays.equals(childOffset, partPositions.get(i))){
					if(getChildAtLocation(partPositions.get(i-4)) != null){
						enginePropellers.forcePut(getChildAtLocation(partPositions.get(i-4)).UUID, childUUID);
					}
				}
			}
		}
	}
	
	@Override
	public void removeChild(String childUUID, boolean playBreakSound){
		super.removeChild(childUUID, playBreakSound);
		landingGears.remove(childUUID);
		engines.remove(childUUID);
		propellers.remove(childUUID);
		enginePropellers.remove(childUUID);
		enginePropellers.inverse().remove(childUUID);
	}
	
	public int getEngineIdOfHitPropeller(String propellerUUID){
		return engines.get(enginePropellers.inverse().get(propellerUUID)).getEntityId();
	}
	
	public List<double[]> getEngineProperties(){
		List<double[]> properties = new ArrayList<double[]>();
		for(EntityEngine engine : engines.values()){
			properties.add(engine.getEngineProperties());
		}
		return properties;
	}
	
	protected EntityWheel[] getWheels(){return landingGears.values().toArray(new EntityWheel[landingGears.size()]);}
	protected EntityPropeller[] getPropellers(){return propellers.values().toArray(new EntityPropeller[propellers.size()]);}
	public EntityPropeller getPropellerForEngine(String engineUUID){return propellers.get(enginePropellers.get(engineUUID));}
	
	protected abstract void initPlaneProperties();

	@Override
    public void saveInventory(){
		if(!worldObj.isRemote){
			EntityChild newChild;
			float boostAmount = 0;
			
			//First, spawn components
			for(int i=1; i<controllerSlot; ++i){
				if(itemChanged[i]){
					float[] position = partPositions.get(i);
					EntityChild child = getChildAtLocation(partPositions.get(i));
					ItemStack stack = getStackInSlot(i);
					if(stack != null){
						if(child != null){
							removeChild(child.UUID, false);
						}
						if(stack.getItem().equals(MFSRegistry.wheelSmall)){
							newChild = new EntityWheelSmall(worldObj, this, this.UUID, position[0], position[1], position[2]);
						}else if(stack.getItem().equals(MFSRegistry.wheelLarge)){
							newChild = new EntityWheelLarge(worldObj, this, this.UUID, position[0], position[1], position[2]);
						}else if(stack.getItem().equals(MFSRegistry.pontoon)){
							newChild = new EntityPontoon(worldObj, this, this.UUID, position[0], position[1] - (position[2] > 0 ? 0 : 0.1F), position[2] + (position[2] > 0 ? 0 : 2));
							EntityPontoonDummy pontoonDummy = new EntityPontoonDummy(worldObj, this, this.UUID, position[0], position[1] + (position[2] > 0 ? 0.25F : 0), position[2] - (position[2] > 0 ? 2 : 0));
							pontoonDummy.setOtherHalf((EntityPontoon) newChild);
							((EntityPontoon) newChild).setOtherHalf(pontoonDummy);
							addChild(pontoonDummy.UUID, pontoonDummy, true);
						}else if(stack.getItem().equals(MFSRegistry.skid)){
							newChild = new EntitySkid(worldObj, this, this.UUID, position[0], position[1], position[2]);
						}else if(stack.getItem() instanceof ItemEngine){
							EntityEngine engine = new EntityEngineAircraft(worldObj, this, this.UUID, position[0], position[1], position[2], stack.getItemDamage(), ((ItemEngine) stack.getItem()).type);
							if(stack.hasTagCompound()){
								engine.hours = stack.getTagCompound().getDouble("hours");
							}
							newChild = engine;
						}else if(stack.getItem().equals(MFSRegistry.propeller)){
							newChild = new EntityPropeller(worldObj, this, this.UUID, position[0], position[1], position[2], stack.getItemDamage());
						}else{
							continue;
						}
						addChild(newChild.UUID, newChild, true);
					}else{
						if(child != null){
							removeChild(child.UUID, false);
						}
					}
				}
			}
			
			//Check to see if any propellers lost their engines
			for(int i=10; i<=13; ++i){
				if(getStackInSlot(i) != null){
					if(getStackInSlot(i-4) == null){
						worldObj.spawnEntityInWorld(new EntityItem(worldObj, this.posX, this.posY, this.posZ, new ItemStack(MFSRegistry.propeller, 1, getStackInSlot(i).getItemDamage())));
						setInventorySlotContents(i, null);
						EntityChild child = getChildAtLocation(partPositions.get(i));
						if(child != null){
							removeChild(child.UUID, false);
						}
					}
				}
			}

			//Next, spawn new seats and chests.
			this.spawnLoadableContents();
			
			//Finally, do instrument and bucket things
			for(int i=0; i<10; ++i){
				if(itemChanged[i+instrumentStartSlot]){
					instrumentList.set(i, getStackInSlot(i+instrumentStartSlot));
				}
			}
			
			if(getStackInSlot(fuelBucketSlot) != null){
				worldObj.spawnEntityInWorld(new EntityItem(worldObj, posX, posY, posZ, getStackInSlot(fuelBucketSlot)));
				setInventorySlotContents(fuelBucketSlot, null);
			}
			this.sendDataToClient();
		}
	}
}

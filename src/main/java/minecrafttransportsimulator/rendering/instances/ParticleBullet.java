package minecrafttransportsimulator.rendering.instances;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.awt.Color;

import org.lwjgl.opengl.GL11;

import minecrafttransportsimulator.baseclasses.BoundingBox;
import minecrafttransportsimulator.baseclasses.Damage;
import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.baseclasses.Point3i;
import minecrafttransportsimulator.items.instances.ItemPart;
import minecrafttransportsimulator.jsondefs.JSONPart.JSONPartBullet.ParticleObject;
import minecrafttransportsimulator.mcinterface.IWrapperEntity;
import minecrafttransportsimulator.mcinterface.MasterLoader;
import minecrafttransportsimulator.packets.instances.PacketBulletHit;
import minecrafttransportsimulator.rendering.components.AParticle;
import minecrafttransportsimulator.rendering.instances.ParticleSmoke;
import minecrafttransportsimulator.rendering.instances.ParticleFlame;
import minecrafttransportsimulator.rendering.components.OBJParser;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.vehicles.parts.PartGun;

/**This part class is special, in that it does not extend APart.
 * This is because bullets do not render as vehicle parts, and instead
 * are particles.  This allows them to be independent of the
 * vehicle that fired them.
 * 
 * As particles, bullets are client-side only.  This prevents them from getting stuck
 * in un-loaded chunks on the server, and prevents the massive network usage that
 * would be required to spawn 100s of bullets from a machine gun into the world.
 * 
 * @author don_bruce
 */

public class ParticleBullet extends AParticle{
	private final ItemPart bullet;
	private final PartGun gun;
	private final int bulletNumber;
	private final double initialVelocity;
	private final double deltaVelocity;
	private final IWrapperEntity gunController;
	private final Point3d blockMotionStep = new Point3d(0D, 0D, 0D);
	
	private final Map<ItemPart, Integer> bulletDisplayLists = new HashMap<ItemPart, Integer>();
	
	private double armorPenetrated;
	private int burnTimeLeft;
	private int accelerationLeft;
	private int timeUntilAirBurst;
	
    public ParticleBullet(Point3d position, Point3d motion, ItemPart bullet, PartGun gun, IWrapperEntity gunController){
    	super(gun.vehicle.world, position, motion);
    	this.bullet = bullet;
    	this.gun = gun;
        this.bulletNumber = gun.bulletsFired;
        this.initialVelocity = motion.length();
        this.burnTimeLeft = bullet.definition.bullet.burnTime;
        this.accelerationLeft = bullet.definition.bullet.accelerationTime;
        this.deltaVelocity = accelerationLeft != 0 ? (bullet.definition.bullet.maxVelocity/20D/10D - initialVelocity) / accelerationLeft : 0D;
        this.gunController = gunController;
        this.timeUntilAirBurst = bullet.definition.bullet.airBurstDelay;
        if(bullet.definition.bullet.blastStrength == 0F) bullet.definition.bullet.blastStrength = 1F;
    }
	
	@Override
	public void update(){
		//Get current velocity and possible damage.
		double velocity = motion.length();
		Damage damage = new Damage("bullet", velocity*bullet.definition.bullet.diameter/5*ConfigSystem.configObject.damage.bulletDamageFactor.value, box, null);
		
		//Check for collided entities and attack them.
		//If we collide with an armored vehicle, try to penetrate it.
		Map<IWrapperEntity, BoundingBox> attackedEntities = world.attackEntities(damage, gun.vehicle, motion);
		if(!attackedEntities.isEmpty()){
			for(IWrapperEntity entity : attackedEntities.keySet()){
				if(attackedEntities.get(entity) != null){
					BoundingBox hitBox = attackedEntities.get(entity);
					if(hitBox.armorThickness != 0){
						if(hitBox.armorThickness < bullet.definition.bullet.armorPenetration*velocity/initialVelocity - armorPenetrated){
							armorPenetrated += hitBox.armorThickness;
							continue;
						}
					}
					MasterLoader.networkInterface.sendToServer(new PacketBulletHit(hitBox, velocity, bullet, gun, bulletNumber, entity, gunController));
					age = maxAge;
				}else{
					box.globalCenter.setTo(entity.getPosition());
					MasterLoader.networkInterface.sendToServer(new PacketBulletHit(box, velocity, bullet, gun, bulletNumber, entity, gunController));
					age = maxAge;
				}
			}
			
			//If we hit something, don't process anything further.
			if(age == maxAge){
				return;
			}
		}
		
		//Didn't hit an entity.  Check for blocks.
		Point3i hitPos = world.getBlockHit(position, motion);
		if(hitPos != null){
			doBulletHit(new Point3d(hitPos), velocity);
			return;
		}
		
		//Didn't hit a block either. Check the air-burst time, if it was used.
		if(bullet.definition.bullet.airBurstDelay != 0) {
			if(this.timeUntilAirBurst == 0) {
				doBulletHit(this.position, velocity);
				return;
			}
			else {
				--this.timeUntilAirBurst;
			}
		}
		
		//Check proximity fuze against any blocks that might be out front
		if(bullet.definition.bullet.proximityFuze != 0) {
			Point3i projectedImpactPoint = gun.vehicle.world.getBlockHit(this.position, motion.copy().normalize().multiply(bullet.definition.bullet.proximityFuze));
			if(projectedImpactPoint != null) {
				this.doBulletHit(this.position, velocity);
				return;
			}
		}
		
		//Now that we have checked for collision, adjust motion to compensate for bullet movement and gravity.
		//Ignore this if the bullet has a (rocket motor) burnTime that hasn't yet expired,
		//And if the bullet is still accelerating, increase the velocity appropriately.
		if (this.burnTimeLeft == 0) {
			motion.multiply(0.98D);
			motion.y -= 0.0245D;
		}
		else if(this.accelerationLeft > 0) {
			--this.burnTimeLeft;
			--this.accelerationLeft;
			motion.multiply((deltaVelocity + velocity)/velocity);
		}
		else {
			--this.burnTimeLeft;
		}
		
		//Create trail particles, if defined
		if (bullet.definition.bullet.particleObjects != null) {
			spawnParticles();
		}
		
		//Send our updated motion to the super to update the position.
		//Doing this last lets us damage on the first update tick.
		super.update();
	}
	
	protected void doBulletHit(Point3d hitPos, double velocity) {
		doBulletHit(new BoundingBox(hitPos, box.widthRadius, box.heightRadius, box.depthRadius), velocity);
	}
	
	protected void doBulletHit(BoundingBox hitBox, double velocity) {
		MasterLoader.networkInterface.sendToServer(new PacketBulletHit(hitBox, velocity, bullet, gun, bulletNumber, null, gunController));
		age = maxAge;
	}
	
	private void spawnParticles() {
		for(ParticleObject particleObject : this.bullet.definition.bullet.particleObjects) {
			//Set initial velocity to the be opposite the direction of motion in the magnitude of the defined velocity.
			//Add a little variation to this.
			Point3d particleVelocity = motion.copy().normalize().multiply(-particleObject.velocity/20D/10D);
			
			//Get the particle's initial position.
			Point3d particlePosition = this.position.copy();
			if(particleObject.pos != null) {
				particlePosition.add(particleObject.pos.copy().rotateFine(new Point3d(0D, this.getYaw(), 0d)).rotateFine(new Point3d(this.getPitch(), 0D, 0D)));
			}

			//Spawn the appropriate type and amount of particles.
			//Change default values from 0 to 1.
			AParticle currentParticle;
			if(particleObject.quantity == 0) particleObject.quantity = 1;
			if(particleObject.scale == 0f && particleObject.toScale == 0f) particleObject.scale = 1f;
			switch(particleObject.type) {
				case "smoke": {
					Color color = particleObject.color != null ? Color.decode(particleObject.color) : Color.decode("#FFFFFF");
					Color toColor = particleObject.toColor != null ? Color.decode(particleObject.toColor) : color;
					if(particleObject.transparency == 0f && particleObject.toTransparency == 0F) particleObject.transparency = 1f;
					for(int i=0; i<particleObject.quantity; i++) {
						currentParticle = new ParticleSmoke(gun.vehicle.world, particlePosition, particleVelocity.copy().add(new Point3d(0.04*Math.random(), 0.04*Math.random(), 0.04*Math.random())), color.getRed()/255F, color.getGreen()/255F, color.getBlue()/255F, particleObject.transparency, particleObject.scale);
						currentParticle.setDeltas(toColor.getRed()/255F, toColor.getGreen()/255F, toColor.getBlue()/255F, particleObject.toTransparency, particleObject.toScale);
						MasterLoader.renderInterface.spawnParticle(currentParticle);
					}
					break;
				}
				case "flame": {
					for(int i=0; i<particleObject.quantity; i++) {
						currentParticle = new ParticleFlame(gun.vehicle.world, particlePosition, particleVelocity.copy().add(new Point3d(0.04*Math.random(), 0.04*Math.random(), 0.04*Math.random())), particleObject.scale);
						currentParticle.deltaScale = (particleObject.toScale - currentParticle.scale) / (currentParticle.maxAge - currentParticle.age);
						MasterLoader.renderInterface.spawnParticle(currentParticle);
					}
					break;
				}
			}
		}
	}
	
	protected double getYaw() {
		return Math.toDegrees(Math.atan2(motion.x, motion.z));
	}
	
	protected double getPitch() {
		return -Math.toDegrees(Math.atan2(motion.y, Math.hypot(motion.x, motion.z)));
	}
	
	protected void rotatePointByOrientation(Point3d point) {
		point.rotateFine(new Point3d(0D, this.getYaw(), 0D)).rotateFine(new Point3d(this.getPitch(), 0D, 0D));
	}
	
	@Override
	public boolean collidesWithBlocks(){
		return false;
	}
	
	@Override
	public float getScale(float partialTicks){
		return 1.0F;
	}
	
	@Override
	public float getSize(){
		return bullet != null ? bullet.definition.bullet.diameter/1000F : super.getSize();
	}
	
	@Override
	protected int generateMaxAge(){
		return 10*20;
	}
	
	@Override
	public int getTextureIndex(){
		return -1;
	}
	
	@Override
	public boolean isBright(){
		return bullet.definition.bullet.types.contains("tracer");
	}
	
	@Override
	public void render(float partialTicks){
        //Parse the model if we haven't already.
        if(!bulletDisplayLists.containsKey(bullet)){
        	Map<String, Float[][]> parsedModel = OBJParser.parseOBJModel(bullet.definition.getModelLocation());
        	int displayListIndex = GL11.glGenLists(1);
    		GL11.glNewList(displayListIndex, GL11.GL_COMPILE);
    		GL11.glBegin(GL11.GL_TRIANGLES);
    		for(Entry<String, Float[][]> entry : parsedModel.entrySet()){
				for(Float[] vertex : entry.getValue()){
					GL11.glTexCoord2f(vertex[3], vertex[4]);
					GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
					GL11.glVertex3f(-vertex[0], vertex[1], vertex[2]);
				}
    		}
    		GL11.glEnd();
    		GL11.glEndList();
        	bulletDisplayLists.put(bullet, displayListIndex);
        }
        
        //Bind the texture for this bullet.
        MasterLoader.renderInterface.bindTexture(bullet.definition.getTextureLocation(bullet.subName));
        
        //Render the parsed model.  Translation will already have been applied, 
        //so we just need to rotate ourselves based on our velocity.
        double yaw = Math.toDegrees(Math.atan2(motion.x, motion.z));
        double pitch = -Math.toDegrees(Math.asin(motion.y/Math.sqrt(motion.x*motion.x+motion.y*motion.y+motion.z*motion.z)));
        GL11.glRotated(yaw, 0, 1, 0);
        GL11.glRotated(pitch, 1, 0, 0);
        GL11.glCallList(bulletDisplayLists.get(bullet));
	}
}

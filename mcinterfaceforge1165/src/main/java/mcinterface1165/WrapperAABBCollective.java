package mcinterface1165;

import java.util.Collection;
import java.util.Optional;

import minecrafttransportsimulator.baseclasses.BoundingBox;
import minecrafttransportsimulator.baseclasses.Point3D;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;

/**
 * This class is essentially a collective list of BoundingBoxes.  It intercepts all AABB
 * calls and does checks for each BoundingBox that's in the passed-in list.
 * Mostly used for entities that need complex collision mapping, because MC don't let you have more
 * than one AABB per entity, but somehow you can have more than one for something as small as a block?
 *
 * @author don_bruce
 */
public class WrapperAABBCollective extends AxisAlignedBB {
    protected final Collection<BoundingBox> boxes;
    protected BoundingBox lastBoxRayTraced;

    public WrapperAABBCollective(BoundingBox encompassingBox, Collection<BoundingBox> boxes) {
        super(encompassingBox.globalCenter.x - encompassingBox.widthRadius, encompassingBox.globalCenter.y - encompassingBox.heightRadius, encompassingBox.globalCenter.z - encompassingBox.depthRadius, encompassingBox.globalCenter.x + encompassingBox.widthRadius, encompassingBox.globalCenter.y + encompassingBox.heightRadius, encompassingBox.globalCenter.z + encompassingBox.depthRadius);
        this.boxes = boxes;
    }

    @Override
    public WrapperAABBCollective inflate(double value) {
        return this;
    }

    @Override
    public boolean intersects(double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        //CHeck super first, as that's the encompassing box.
        if (super.intersects(otherMinX, otherMinY, otherMinZ, otherMaxX, otherMaxY, otherMaxZ)) {
            for (BoundingBox testBox : boxes) {
                if (otherMaxX > testBox.globalCenter.x - testBox.widthRadius && otherMinX < testBox.globalCenter.x + testBox.widthRadius && otherMaxY > testBox.globalCenter.y - testBox.heightRadius && otherMinY < testBox.globalCenter.y + testBox.heightRadius && otherMaxZ > testBox.globalCenter.z - testBox.depthRadius && otherMinZ < testBox.globalCenter.z + testBox.depthRadius) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean contains(Vector3d vec) {
        return this.intersects(vec.x, vec.y, vec.z, vec.x, vec.y, vec.z);
    }

    @Override
    public Optional<Vector3d> clip(Vector3d vecA, Vector3d vecB) {
        lastBoxRayTraced = null;
        //Check all the bounding boxes for collision to see if we hit one of them.
        Point3D start = new Point3D(vecA.x, vecA.y, vecA.z);
        Point3D end = new Point3D(vecB.x, vecB.y, vecB.z);
        Point3D intersection = null;
        for (BoundingBox testBox : boxes) {
            Point3D testIntersection = testBox.getIntersectionPoint(start, end);
            if (testIntersection != null) {
                if (intersection == null || testIntersection.distanceTo(start) < intersection.distanceTo(start)) {
                    intersection = testIntersection;
                    lastBoxRayTraced = testBox;
                }
            }
        }
        if (intersection != null) {
            return Optional.of(new Vector3d(intersection.x, intersection.y, intersection.z));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Helper method that's akin to MC's older collision methods in 1.12.2, just here rather than
     * in a VoxelShape.
     */
    public Vector3d getCollision(Vector3d movement, AxisAlignedBB testBox) {
        double x = movement.x != 0 ? calculateXOffset(testBox, movement.x) : 0;
        double y = movement.y != 0 ? calculateYOffset(testBox, movement.y) : 0;
        double z = movement.z != 0 ? calculateZOffset(testBox, movement.z) : 0;
        return new Vector3d(x, y, z);
    }

    private double calculateXOffset(AxisAlignedBB box, double offset) {
        for (BoundingBox testBox : boxes) {
            if (box.maxY > testBox.globalCenter.y - testBox.heightRadius && box.minY < testBox.globalCenter.y + testBox.heightRadius && box.maxZ > testBox.globalCenter.z - testBox.depthRadius && box.minZ < testBox.globalCenter.z + testBox.depthRadius) {
                if (offset > 0.0D) {
                    //Positive offset, box.maxX <= this.minX.
                    double collisionDepth = testBox.globalCenter.x - testBox.widthRadius - box.maxX;
                    if (collisionDepth >= 0 && collisionDepth < offset) {
                        offset = collisionDepth;
                    }
                } else if (offset < 0.0D) {
                    //Negative offset, box.minX >= this.maxX.
                    double collisionDepth = testBox.globalCenter.x + testBox.widthRadius - box.minX;
                    if (collisionDepth <= 0 && collisionDepth > offset) {
                        offset = collisionDepth;
                    }
                }
            }
        }
        return offset;
    }
    
    private double calculateYOffset(AxisAlignedBB box, double offset) {
        for (BoundingBox testBox : boxes) {
            if (box.maxX > testBox.globalCenter.x - testBox.widthRadius && box.minX < testBox.globalCenter.x + testBox.widthRadius && box.maxZ > testBox.globalCenter.z - testBox.depthRadius && box.minZ < testBox.globalCenter.z + testBox.depthRadius) {
                if (offset > 0.0D) {
                    //Positive offset, box.maxX <= this.minX.
                    double collisionDepth = testBox.globalCenter.y - testBox.heightRadius - box.maxY;
                    if (collisionDepth >= 0 && collisionDepth < offset) {
                        offset = collisionDepth;
                    }
                } else if (offset < 0.0D) {
                    //Negative offset, box.minX >= this.maxX.
                    double collisionDepth = testBox.globalCenter.y + testBox.heightRadius - box.minY;
                    if (collisionDepth <= 0 && collisionDepth > offset) {
                        offset = collisionDepth;
                    }
                }
            }
        }
        return offset;
    }
    
    private double calculateZOffset(AxisAlignedBB box, double offset) {
        for (BoundingBox testBox : boxes) {
            if (box.maxX > testBox.globalCenter.x - testBox.widthRadius && box.minX < testBox.globalCenter.x + testBox.widthRadius && box.maxY > testBox.globalCenter.y - testBox.heightRadius && box.minY < testBox.globalCenter.y + testBox.heightRadius) {
                if (offset > 0.0D) {
                    //Positive offset, box.maxX <= this.minX.
                    double collisionDepth = testBox.globalCenter.z - testBox.depthRadius - box.maxZ;
                    if (collisionDepth >= 0 && collisionDepth < offset) {
                        offset = collisionDepth;
                    }
                } else if (offset < 0.0D) {
                    //Negative offset, box.minX >= this.maxX.
                    double collisionDepth = testBox.globalCenter.z + testBox.depthRadius - box.minZ;
                    if (collisionDepth <= 0 && collisionDepth > offset) {
                        offset = collisionDepth;
                    }
                }
            }
        }
        return offset;
    }
}

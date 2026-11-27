package seam.core;

import arc.struct.*;
import mindustry.*;
import mindustry.async.*;
import mindustry.async.PhysicsProcess.*;
import mindustry.gen.*;
import seam.gen.*;

/** Helper to detach a unit's physics body before moving the same instance to another world. */
public final class Physics{
    private Physics(){}

    /** Joins native work and removes a unit's physical body immediately. Call in the source world before re-adding the unit elsewhere. */
    public static void remove(Unit unit){
        Context.checkState();
        if(unit == null) throw new IllegalArgumentException("A unit is required.");
        Workers.completeAsync();
        PhysicsProcess process = Vars.unitPhysics;
        Seq<PhysicRef> references = process == null ? null : Engine.physicsRefs(process);
        PhysicRef ref = references == null ? null : references.find(value -> value.entity == unit);
        if(ref == null){
            if(unit.hasPhysicsRef) throw new IllegalStateException("Unit has no physics body in the installed world.");
            return;
        }
        if(ref.lastLayer >= 0) Engine.physicsPhysics(process)[ref.lastLayer].remove(ref.body);
        Engine.physicsPendingAdds(process).remove(ref, true);
        references.remove(ref, true);
        unit.hasPhysicsRef = false;
    }
}

package seam.net;

import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.gen.*;
import seam.core.*;

import java.io.*;

/** Native sync record codec and replica interpolation for non-player entities and buildings. */
public final class EntitySync{
    private EntitySync(){}

    /** Serializes an active non-player entity in the current world. */
    public static byte[] write(Syncc entity){
        world();
        if(entity == null || entity instanceof Player || Groups.sync.getByID(entity.id()) != entity){
            throw new IllegalArgumentException("An installed non-player sync entity is required.");
        }
        var bytes = new ByteArrayOutputStream();
        try(var output = new DataOutputStream(bytes)){
            output.writeInt(entity.id());
            output.writeByte(entity.classId());
            entity.beforeWrite();
            entity.writeSync(new Writes(output));
        }catch(IOException error){
            throw new RuntimeException(error);
        }
        return bytes.toByteArray();
    }

    /** Applies one trusted server record in the installed client world; returns null for a retired ID. */
    public static @Nullable Syncc read(byte[] record){
        client();
        if(record == null || record.length < 5) throw new IllegalArgumentException("An entity snapshot record is required.");
        try(var input = new DataInputStream(new ByteArrayInputStream(record))){
            int id = input.readInt(), type = input.readUnsignedByte();
            Syncc entity = Groups.sync.getByID(id);
            if(type == (Vars.player.classId() & 255) || id == Vars.player.id() || entity instanceof Player){
                throw new IllegalArgumentException("The snapshot overlaps the host player or uses a player class.");
            }
            if(EntityMapping.map(type) == null || entity != null && (entity.classId() & 255) != type){
                throw new IllegalArgumentException("The snapshot's entity class does not match its ID.");
            }
            if(entity == null && Vars.netClient.isEntityUsed(id)) return null;
            input.reset();
            NetClient.readSyncEntity(input, new Reads(input));
            if(input.available() != 0) throw new IllegalArgumentException("Trailing entity snapshot data.");
            return Groups.sync.getByID(id);
        }catch(IOException error){
            throw new RuntimeException(error);
        }
    }

    /** Advances client interpolation for all non-player sync entities in the current world. */
    public static void interpolate(){
        client();
        for(Syncc entity : Groups.sync){
            if(!(entity instanceof Player)) entity.interpolate();
        }
    }

    /** Removes a replica and retires its ID in this world, including when removal arrives before its first snapshot. */
    public static void remove(int id){
        client();
        Syncc entity = Groups.sync.getByID(id);
        if(id == Vars.player.id() || entity instanceof Player) throw new IllegalArgumentException("The host player cannot be removed.");
        if(entity != null) entity.remove();
        Vars.netClient.addRemovedEntity(id);
    }

    /** Serializes an active building in the current world. */
    public static byte[] writeBuilding(Building building){
        world();
        if(building == null || building.tile == null || Vars.world.build(building.pos()) != building){
            throw new IllegalArgumentException("A building on an installed tile is required.");
        }
        var bytes = new ByteArrayOutputStream();
        try(var output = new DataOutputStream(bytes)){
            output.writeInt(building.pos());
            output.writeShort(building.block.id);
            output.writeByte(building.version());
            building.writeSync(new Writes(output));
        }catch(IOException error){
            throw new RuntimeException(error);
        }
        return bytes.toByteArray();
    }

    /** Updates a matching building at its center tile; returns null if empty or a different block. */
    public static @Nullable Building readBuilding(byte[] record){
        client();
        if(record == null || record.length < 7) throw new IllegalArgumentException("A block snapshot record is required.");
        try(var input = new DataInputStream(new ByteArrayInputStream(record))){
            int position = input.readInt(), block = input.readUnsignedShort();
            byte version = input.readByte();
            Building building = Vars.world.build(position);
            if(building == null || building.pos() != position || (building.block.id & 65535) != block) return null;
            if(version != building.version()) throw new IllegalArgumentException("The block snapshot version does not match the building.");
            building.readSync(new Reads(input), version);
            if(input.available() != 0) throw new IllegalArgumentException("Trailing block snapshot data.");
            return building;
        }catch(IOException error){
            throw new RuntimeException(error);
        }
    }

    private static void world(){
        Scopes.checkState();
        Context current = Context.current();
        if(current == null) throw new IllegalStateException("Install an entity's world before synchronizing it.");
        Scopes.checkUsable(current);
    }

    private static void client(){
        world();
        if(Vars.net == null || !Vars.net.client() || Vars.netClient == null || Vars.player == null){
            throw new IllegalStateException("Install a replica world on a connected client before applying snapshots.");
        }
    }
}

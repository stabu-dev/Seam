package seam;

import arc.math.geom.*;
import arc.struct.*;
import mindustry.world.*;

public final class SeamRenderInvalidationQueue{
    private final IntMap<SeamRenderInvalidation> pending = new IntMap<>();
    private final Seq<SeamRenderInvalidation> lastDrained = new Seq<>();

    private boolean fullDirty;
    private long version;
    private long lastDrainVersion;

    public int size(){
        return pending.size + (fullDirty ? 1 : 0);
    }

    public boolean empty(){
        return pending.isEmpty() && !fullDirty;
    }

    public boolean fullDirty(){
        return fullDirty;
    }

    public long version(){
        return version;
    }

    public long lastDrainVersion(){
        return lastDrainVersion;
    }

    public Seq<SeamRenderInvalidation> lastDrained(){
        return lastDrained.copy();
    }

    public void markFull(){
        fullDirty = true;
        pending.clear();
        version++;
    }

    public void mark(SeamRuntime runtime, int x, int y, SeamRenderInvalidationType... types){
        mark(runtime, x, y, 0, types);
    }

    public void mark(SeamRuntime runtime, int x, int y, int radius, SeamRenderInvalidationType... types){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(!runtime.worldReady()){
            return;
        }

        if(x < 0 || y < 0 || x >= runtime.world.width() || y >= runtime.world.height()){
            return;
        }

        if(fullDirty){
            return;
        }

        int mask = mask(types);
        int tilePos = Point2.pack(x, y);

        SeamRenderInvalidation existing = pending.get(tilePos);

        if(existing == null){
            pending.put(tilePos, SeamRenderInvalidation.tile(runtime.id, x, y, tilePos, mask, radius));
        }else{
            existing.merge(mask, radius);
        }

        version++;
    }

    public void markPos(SeamRuntime runtime, int tilePos, SeamRenderInvalidationType... types){
        mark(runtime, Point2.x(tilePos), Point2.y(tilePos), 0, types);
    }

    public void markAround(SeamRuntime runtime, int centerX, int centerY, int radius, SeamRenderInvalidationType... types){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(!runtime.worldReady()){
            return;
        }

        int minX = Math.max(0, centerX - radius);
        int minY = Math.max(0, centerY - radius);
        int maxX = Math.min(runtime.world.width() - 1, centerX + radius);
        int maxY = Math.min(runtime.world.height() - 1, centerY + radius);

        for(int x = minX; x <= maxX; x++){
            for(int y = minY; y <= maxY; y++){
                mark(runtime, x, y, radius, types);
            }
        }
    }

    public void blockChanged(SeamRuntime runtime, int x, int y, Block previous, Block current){
        int previousSize = previous == null ? 1 : previous.size;
        int currentSize = current == null ? 1 : current.size;
        int radius = Math.max(previousSize, currentSize) + 2;

        markAround(
        runtime,
        x,
        y,
        radius,
        SeamRenderInvalidationType.tile,
        SeamRenderInvalidationType.block,
        SeamRenderInvalidationType.light,
        SeamRenderInvalidationType.shadow,
        SeamRenderInvalidationType.proximity
        );
    }

    public void floorChanged(SeamRuntime runtime, int x, int y){
        markAround(
        runtime,
        x,
        y,
        1,
        SeamRenderInvalidationType.tile,
        SeamRenderInvalidationType.floor,
        SeamRenderInvalidationType.light
        );
    }

    public void configChanged(SeamRuntime runtime, Tile tile, Object value){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(tile == null){
            return;
        }

        int radius = tile.block() == null ? 2 : tile.block().size + 2;

        markAround(
        runtime,
        tile.x,
        tile.y,
        radius,
        SeamRenderInvalidationType.tile,
        SeamRenderInvalidationType.config,
        SeamRenderInvalidationType.light
        );

        if(value instanceof Integer pos){
            markPos(
            runtime,
            pos,
            SeamRenderInvalidationType.tile,
            SeamRenderInvalidationType.config,
            SeamRenderInvalidationType.light
            );
        }
    }

    public Seq<SeamRenderInvalidation> drain(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        lastDrained.clear();

        if(fullDirty){
            lastDrained.add(SeamRenderInvalidation.full(runtime.id));
        }else{
            for(SeamRenderInvalidation invalidation : pending.values()){
                lastDrained.add(invalidation.copy());
            }
        }

        pending.clear();
        fullDirty = false;
        lastDrainVersion = version;

        return lastDrained.copy();
    }

    public void clear(){
        pending.clear();
        lastDrained.clear();
        fullDirty = false;
        version++;
        lastDrainVersion = version;
    }

    private int mask(SeamRenderInvalidationType... types){
        if(types == null || types.length == 0){
            return SeamRenderInvalidationType.tile.mask;
        }

        int mask = 0;

        for(SeamRenderInvalidationType type : types){
            if(type != null){
                mask |= type.mask;
            }
        }

        return mask == 0 ? SeamRenderInvalidationType.tile.mask : mask;
    }
}
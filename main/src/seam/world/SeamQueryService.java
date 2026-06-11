package seam.world;

import seam.core.*;
import seam.runtime.*;
import seam.runtime.control.*;
import seam.world.tiles.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

public final class SeamQueryService{
    private final SeamRuntimeRegistry runtimes;
    private final SeamRuntimeExecutor executor;

    public SeamQueryService(SeamRuntimeRegistry runtimes, SeamRuntimeExecutor executor){
        if(runtimes == null){
            throw new NullPointerException("runtimes");
        }

        if(executor == null){
            throw new NullPointerException("executor");
        }

        this.runtimes = runtimes;
        this.executor = executor;
    }

    public SeamTileSnapshot tile(int runtimeId, int x, int y){
        return tile(SeamTileRef.of(runtimeId, x, y));
    }

    public SeamTileSnapshot tilePos(int runtimeId, int tilePos){
        return tile(SeamTileRef.ofPos(runtimeId, tilePos));
    }

    public SeamTileSnapshot tile(SeamTileRef ref){
        if(ref == null){
            throw new NullPointerException("ref");
        }

        SeamRuntime runtime = runtimes.get(ref.runtimeId);

        if(runtime == null){
            return SeamTileSnapshot.failure(ref, "runtime not found");
        }

        if(runtime.disposed()){
            return SeamTileSnapshot.failure(ref, "runtime is disposed");
        }

        if(!runtime.loaded()){
            return SeamTileSnapshot.failure(ref, "runtime is not loaded");
        }

        if(!runtime.worldReady()){
            return SeamTileSnapshot.failure(ref, "runtime world is not ready");
        }

        if(ref.x < 0 || ref.y < 0 || ref.x >= runtime.world.width() || ref.y >= runtime.world.height()){
            return SeamTileSnapshot.failure(ref, "tile coordinates are out of bounds");
        }

        try{
            return executor.callRegisteredExclusive(runtime, SeamPhase.validate, active -> {
                Tile tile = active.world.tile(ref.x, ref.y);

                if(tile == null){
                    return SeamTileSnapshot.failure(ref, "tile not found");
                }

                Building build = tile.build;

                String blockName = tile.block() == null ? null : tile.block().name;
                String floorName = tile.floor() == null ? null : tile.floor().name;

                if(build == null){
                    return SeamTileSnapshot.success(
                    ref,
                    blockName,
                    floorName,
                    false,
                    null,
                    null,
                    0,
                    false,
                    0,
                    false,
                    null,
                    0f,
                    false,
                    false,
                    0
                    );
                }

                boolean hasItems = build.items != null;
                int itemTotal = hasItems ? build.items.total() : 0;

                boolean hasLiquids = build.liquids != null;
                Liquid currentLiquid = hasLiquids ? build.liquids.current() : null;
                String currentLiquidName = currentLiquid == null ? null : currentLiquid.name;
                float currentLiquidAmount = hasLiquids ? build.liquids.currentAmount() : 0f;

                boolean hasPower = build.power != null;
                boolean hasPowerGraph = hasPower && build.power.graph != null;
                int powerLinkCount = hasPower ? build.power.links.size : 0;

                return SeamTileSnapshot.success(
                ref,
                blockName,
                floorName,
                true,
                build.getClass().getName(),
                build.team,
                build.rotation,
                hasItems,
                itemTotal,
                hasLiquids,
                currentLiquidName,
                currentLiquidAmount,
                hasPower,
                hasPowerGraph,
                powerLinkCount
                );
            });
        }catch(Throwable throwable){
            return SeamTileSnapshot.failure(ref, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }
}
package seam.core;

import seam.graphics.*;
import seam.graphics.draw.*;
import seam.graphics.invalidation.*;
import seam.graphics.pick.*;
import seam.graphics.view.*;
import seam.runtime.*;
import seam.runtime.control.*;
import seam.world.*;
import seam.world.config.*;
import seam.world.construction.*;
import seam.world.terrain.*;
import arc.*;
import mindustry.game.EventType.*;
import mindustry.world.*;

public final class SeamServices{
    public final SeamRuntimeStack stack = new SeamRuntimeStack();
    public final SeamRuntimeRegistry runtimes = new SeamRuntimeRegistry();
    public final SeamRuntimeExecutor executor = new SeamRuntimeExecutor(runtimes, stack);
    public final SeamEngine engine = new SeamEngine(runtimes, stack, executor);
    public final SeamConfigService config = new SeamConfigService(runtimes, executor);
    public final SeamBuildService builds = new SeamBuildService(runtimes, executor);
    public final SeamTerrainService terrain = new SeamTerrainService(runtimes, executor);
    public final SeamQueryService query = new SeamQueryService(runtimes, executor);
    public final SeamViewRegistry views = new SeamViewRegistry();
    public final SeamPickService picks = new SeamPickService(runtimes, views, query);
    public final SeamRenderService rendering = new SeamRenderService(runtimes, views);
    public final SeamDrawScope drawScope = new SeamDrawScope(stack);
    public final SeamWorldDraw worldDraw = new SeamWorldDraw(runtimes, views, rendering, drawScope);

    private SeamRuntime mainRuntime;
    private boolean eventsInstalled;

    public SeamRuntime mainRuntime(){
        return mainRuntime;
    }

    public void refreshMainRuntime(){
        runtimes.refreshMain();
        mainRuntime = runtimes.main();
    }

    public void installEvents(Runnable refreshMainRuntime){
        if(refreshMainRuntime == null){
            throw new NullPointerException("refreshMainRuntime");
        }

        if(eventsInstalled){
            return;
        }

        eventsInstalled = true;

        Events.on(WorldLoadEvent.class, event -> refreshMainRuntime.run());

        Events.on(ResetEvent.class, event -> {
            clearForReset();
            refreshMainRuntime.run();
        });

        Events.on(BlockDestroyEvent.class, event -> markActiveRuntimeTileDirty(event.tile));
        Events.on(TileChangeEvent.class, event -> markActiveRuntimeTileDirty(event.tile));

        Events.run(Trigger.afterGameUpdate, engine::update);
        Events.run(Trigger.draw, worldDraw::draw);
    }

    public void clearForReset(){
        worldDraw.clear();
        rendering.clear();
        views.clear();
        runtimes.clearSubworlds();
    }

    private void markActiveRuntimeTileDirty(Tile tile){
        if(tile == null || !stack.active()){
            return;
        }

        SeamRuntime runtime = stack.current();

        if(runtime == null || runtime.main() || runtime.disposed() || !runtime.worldReady()){
            return;
        }

        if(tile.x < 0 || tile.y < 0 || tile.x >= runtime.world.width() || tile.y >= runtime.world.height()){
            return;
        }

        if(runtime.world.tile(tile.x, tile.y) != tile){
            return;
        }

        Block block = tile.block();
        int radius = Math.max(3, (block == null ? 1 : block.size) + 3);

        runtime.renderInvalidation.markAround(
        runtime,
        tile.x,
        tile.y,
        radius,
        SeamRenderInvalidationType.tile,
        SeamRenderInvalidationType.block,
        SeamRenderInvalidationType.light,
        SeamRenderInvalidationType.shadow,
        SeamRenderInvalidationType.proximity
        );
    }
}

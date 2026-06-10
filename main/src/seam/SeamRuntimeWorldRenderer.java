package seam;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;

public final class SeamRuntimeWorldRenderer{
    private static final float rangePad = 0.001f;

    private final SeamRuntime runtime;
    private final SeamRenderService rendering;
    private final SeamDrawScope drawScope;
    private final SeamShadowRenderCache shadows;

    private final Rect entityRuntimeBounds = new Rect();

    public SeamRuntimeWorldRenderer(SeamRuntime runtime, SeamRenderService rendering, SeamDrawScope drawScope){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(rendering == null){
            throw new NullPointerException("rendering");
        }

        if(drawScope == null){
            throw new NullPointerException("drawScope");
        }

        this.runtime = runtime;
        this.rendering = rendering;
        this.drawScope = drawScope;
        this.shadows = new SeamShadowRenderCache(runtime);
    }

    public SeamRuntime runtime(){
        return runtime;
    }

    public SeamShadowRenderCache shadows(){
        return shadows;
    }

    public void dispose(){
        shadows.dispose();
    }

    public boolean render(SeamView view, Rect hostBounds, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(view == null){
            throw new NullPointerException("view");
        }

        if(hostBounds == null){
            throw new NullPointerException("hostBounds");
        }

        if(settings == null){
            throw new NullPointerException("settings");
        }

        if(stats == null){
            throw new NullPointerException("stats");
        }

        if(view.runtimeId() != runtime.id){
            return false;
        }

        SeamRenderViewBatch batch = rendering.queryView(view.id(), hostBounds);

        if(!batch.success){
            stats.batchesFailed++;
            return false;
        }

        scheduleRenderRange(view);
        scheduleFloor(view, hostBounds, settings, stats);
        scheduleShadows(view, hostBounds, settings, stats);
        queueVanillaRuntimeDraw(view, hostBounds, batch, settings, stats);

        return true;
    }

    private void scheduleRenderRange(SeamView view){
        float min = view.hostZ(Layer.min);
        float max = view.hostZ(Layer.max);
        float center = (min + max) / 2f;
        float range = Math.abs(max - min) / 2f + rangePad;

        Draw.drawRange(
        center,
        range,
        () -> drawScope.beginRenderRange(runtime, view, SeamPhase.renderWorld),
        drawScope::endRenderRange
        );
    }

    private void scheduleFloor(SeamView view, Rect hostBounds, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(!settings.drawFloors){
            return;
        }

        Draw.draw(view.hostZ(Layer.floor), () -> {
            SeamFloorDrawResult result = rendering.drawFloor(view.id(), hostBounds);
            stats.addFloorResult(result);
        });
    }

    private void scheduleShadows(SeamView view, Rect hostBounds, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(!settings.drawStaticShadows){
            return;
        }

        Draw.draw(view.hostZ(Layer.block - 1f), () -> {
            shadows.draw(view, hostBounds, viewerTeam());

            stats.shadowMasksPrepared++;
            stats.staticShadowsDrawn += shadows.visibleShadowCount();
        });
    }

    private void queueVanillaRuntimeDraw(
    SeamView view,
    Rect hostBounds,
    SeamRenderViewBatch batch,
    SeamWorldDrawSettings settings,
    SeamWorldDrawStats stats
    ){
        drawScope.beginQueue(runtime, view, SeamPhase.renderWorld);

        try{
            if(settings.drawBlocks){
                drawBlocksVanilla(batch, settings, stats);
            }

            if(settings.drawDrawEntities){
                drawDrawEntities(view, hostBounds, stats);
            }

            if(settings.drawLights){
                stats.lightsSkipped += batch.lightCount();
            }
        }finally{
            drawScope.endQueue();
        }
    }

    private void drawBlocksVanilla(SeamRenderViewBatch batch, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        Team pteam = viewerTeam();

        for(int i = 0; i < batch.tiles.size; i++){
            Tile tile = batch.tiles.items[i];

            if(tile == null){
                continue;
            }

            stats.tilesVisited++;

            Block block = tile.block();
            Building build = tile.build;

            Draw.z(Layer.block);

            boolean visible = build == null || pteam == null || !build.inFogTo(pteam);
            boolean wasVisible = build != null && build.wasVisible;

            if(block != Blocks.air && (visible || wasVisible)){
                block.drawBase(tile);

                stats.blocksDrawn++;

                Draw.reset();
                Draw.z(Layer.block);

                if(settings.drawCustomShadows && block.customShadow){
                    Draw.z(Layer.block - 1f);
                    block.drawShadow(tile);
                    Draw.z(Layer.block);

                    stats.customShadowsDrawn++;
                }

                if(build != null){
                    if(visible){
                        if(pteam != null && settings.updateVisibilityFlags){
                            build.visibleFlags |= 1L << pteam.id;
                        }

                        if(!build.wasVisible){
                            build.wasVisible = true;
                            shadows.updateShadow(build);
                        }
                    }

                    if(settings.drawCracks && build.damaged()){
                        Draw.z(Layer.blockCracks);
                        build.drawCracks();
                        Draw.z(Layer.block);

                        stats.cracksDrawn++;
                    }

                    if(settings.drawTeamOverlays && pteam != null && build.team != pteam){
                        if(build.block.drawTeamOverlay){
                            build.drawTeam();
                            Draw.z(Layer.block);

                            stats.teamOverlaysDrawn++;
                        }
                    }else if(settings.drawStatus && shouldDrawStatus(block, settings)){
                        build.drawStatus();

                        stats.statusDrawn++;
                    }
                }

                Draw.reset();
            }
        }
    }

    private void drawDrawEntities(SeamView view, Rect hostBounds, SeamWorldDrawStats stats){
        computeRuntimeBounds(view.projection(), hostBounds, entityRuntimeBounds);

        for(Object object : Groups.draw){
            if(!(object instanceof Drawc draw)){
                continue;
            }

            float clip = draw.clipSize();

            if(clip <= 0f){
                clip = Vars.tilesize * 2f;
            }

            if(entityRuntimeBounds.overlaps(draw.x() - clip / 2f, draw.y() - clip / 2f, clip, clip)){
                draw.draw();
                stats.drawEntitiesQueued++;
            }
        }
    }

    private void computeRuntimeBounds(SeamProjection projection, Rect hostBounds, Rect out){
        Vec2 a = projection.hostToRuntimeWorld(hostBounds.x, hostBounds.y, Tmp.v1);
        Vec2 b = projection.hostToRuntimeWorld(hostBounds.x + hostBounds.width, hostBounds.y, Tmp.v2);
        Vec2 c = projection.hostToRuntimeWorld(hostBounds.x + hostBounds.width, hostBounds.y + hostBounds.height, Tmp.v3);
        Vec2 d = projection.hostToRuntimeWorld(hostBounds.x, hostBounds.y + hostBounds.height, Tmp.v4);

        float minX = Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x));
        float minY = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
        float maxX = Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x));
        float maxY = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));

        out.set(minX, minY, maxX - minX, maxY - minY);
        out.grow(Vars.tilesize * 6f);

        float worldW = runtime.world.unitWidth();
        float worldH = runtime.world.unitHeight();

        float x1 = Math.max(0f, out.x);
        float y1 = Math.max(0f, out.y);
        float x2 = Math.min(worldW, out.x + out.width);
        float y2 = Math.min(worldH, out.y + out.height);

        if(x2 < x1 || y2 < y1){
            out.set(0f, 0f, 0f, 0f);
        }else{
            out.set(x1, y1, x2 - x1, y2 - y1);
        }
    }

    private boolean shouldDrawStatus(Block block, SeamWorldDrawSettings settings){
        if(block == null || !block.hasConsumers){
            return false;
        }

        if(!settings.respectVanillaStatusToggle){
            return true;
        }

        return Vars.renderer != null && Vars.renderer.drawStatus;
    }

    private Team viewerTeam(){
        if(Vars.player == null){
            return null;
        }

        return Vars.player.team();
    }

    @Override
    public String toString(){
        return "SeamRuntimeWorldRenderer{" +
        "runtimeId=" + runtime.id +
        ", shadows=" + shadows +
        '}';
    }
}
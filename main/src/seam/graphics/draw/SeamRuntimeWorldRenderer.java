package seam.graphics.draw;

import seam.core.*;
import seam.graphics.*;
import seam.graphics.cache.*;
import seam.graphics.view.*;
import seam.runtime.*;
import seam.world.*;
import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;

import java.lang.reflect.*;

public final class SeamRuntimeWorldRenderer{
    private final SeamRuntime runtime;
    private final SeamRenderService rendering;
    private final SeamDrawScope drawScope;
    private final SeamShadowRenderCache shadows;

    private final Rect entityRuntimeBounds = new Rect();
    private final ObjectSet<Drawc> drawnDrawEntities = new ObjectSet<>();

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

        shadows.applyInvalidations(runtime.renderInvalidation.lastDrained());

        Draw.draw(view.hostLayerZ(), () -> renderIsolated(view, hostBounds, batch, settings, stats));

        return true;
    }

    private void renderIsolated(
    SeamView view,
    Rect hostBounds,
    SeamRenderViewBatch batch,
    SeamWorldDrawSettings settings,
    SeamWorldDrawStats stats
    ){
        LightTrap lightTrap = LightTrap.capture();

        boolean restoreDrawLight = false;
        boolean previousDrawLight = false;

        boolean restoreLightingRule = false;
        boolean previousLightingRule = false;

        drawScope.beginIsolated(runtime, view, SeamPhase.renderWorld);

        try{
            if(Vars.renderer != null){
                previousDrawLight = Vars.renderer.drawLight;
                restoreDrawLight = true;
                Vars.renderer.drawLight = false;
            }

            previousLightingRule = runtime.state.rules.lighting;
            restoreLightingRule = true;
            runtime.state.rules.lighting = false;

            scheduleRuntimeScreenPasses(settings, stats);
            scheduleFloor(view, hostBounds, settings, stats);
            scheduleStaticShadows(view, hostBounds, settings, stats);

            if(settings.drawBlocks){
                drawBlocksVanilla(batch, settings, stats);
            }

            if(settings.drawDrawEntities){
                drawDrawEntities(view, hostBounds, stats);
            }

            if(settings.drawLights){
                stats.lightsSkipped += batch.lightCount();
            }

            stats.isolatedBatchesDrawn++;
        }finally{
            Throwable failure = null;

            try{
                drawScope.endIsolated();
            }catch(Throwable throwable){
                failure = throwable;
            }

            try{
                lightTrap.close();
            }catch(Throwable throwable){
                if(failure == null){
                    failure = throwable;
                }
            }

            if(restoreLightingRule){
                runtime.state.rules.lighting = previousLightingRule;
            }

            if(restoreDrawLight && Vars.renderer != null){
                Vars.renderer.drawLight = previousDrawLight;
            }

            if(failure != null){
                if(failure instanceof RuntimeException runtimeException){
                    throw runtimeException;
                }

                throw new RuntimeException(failure);
            }
        }
    }

    private void scheduleRuntimeScreenPasses(SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(Vars.renderer == null){
            return;
        }

        if(settings.drawBloom && Vars.renderer.bloom != null){
            Draw.draw(Layer.bullet - 0.02f, () -> {
                Vars.renderer.bloom.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
                Vars.renderer.bloom.setBloomIntensity(Core.settings.getInt("bloomintensity", 6) / 4f + 1f);
                Vars.renderer.bloom.blurPasses = Core.settings.getInt("bloomblur", 1);
                Vars.renderer.bloom.capture();
                stats.bloomCaptures++;
            });

            Draw.draw(Layer.effect + 0.02f, () -> {
                Vars.renderer.bloom.render();
                stats.bloomRenders++;
            });
        }

        if(settings.drawAnimatedShields && Vars.renderer.animateShields && Shaders.shield != null && Vars.renderer.effectBuffer != null){
            Draw.drawRange(
            Layer.shields,
            settings.screenShaderRange,
            () -> {
                Vars.renderer.effectBuffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
                Vars.renderer.effectBuffer.begin(Color.clear);
            },
            () -> {
                Vars.renderer.effectBuffer.end();
                Vars.renderer.effectBuffer.blit(Shaders.shield);
                stats.animatedShieldPasses++;
            }
            );
        }

        if(settings.drawAnimatedBuildBeams && Shaders.buildBeam != null && Vars.renderer.effectBuffer != null){
            Draw.drawRange(
            Layer.buildBeam,
            settings.screenShaderRange,
            () -> {
                Vars.renderer.effectBuffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
                Vars.renderer.effectBuffer.begin(Color.clear);
            },
            () -> {
                Vars.renderer.effectBuffer.end();
                Vars.renderer.effectBuffer.blit(Shaders.buildBeam);
                stats.animatedBuildBeamPasses++;
            }
            );
        }
    }

    private void scheduleFloor(SeamView view, Rect hostBounds, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(!settings.drawFloors){
            return;
        }

        Draw.draw(Layer.floor, () -> {
            SeamFloorDrawResult result = rendering.drawFloor(view.id(), hostBounds);
            stats.addFloorResult(result);
        });
    }

    private void scheduleStaticShadows(SeamView view, Rect hostBounds, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(!settings.drawStaticShadows){
            return;
        }

        Draw.draw(Layer.block - 1f, () -> {
            shadows.draw(view, hostBounds, viewerTeam());

            stats.shadowMasksPrepared++;
            stats.staticShadowsDrawn += shadows.visibleShadowCount();
        });
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

            if(block == null || block == Blocks.air){
                continue;
            }

            Draw.z(Layer.block);

            boolean visible = build == null || pteam == null || !build.inFogTo(pteam);
            boolean wasVisible = build != null && build.wasVisible;

            if(!visible && !wasVisible){
                continue;
            }

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

    private void drawDrawEntities(SeamView view, Rect hostBounds, SeamWorldDrawStats stats){
        view.projection().runtimeBounds(runtime, hostBounds, Vars.tilesize * 8f, entityRuntimeBounds);

        drawnDrawEntities.clear();

        drawDrawGroup(Groups.puddle, entityRuntimeBounds, stats);
        drawDrawGroup(Groups.fire, entityRuntimeBounds, stats);
        drawDrawGroup(Groups.bullet, entityRuntimeBounds, stats);
        drawDrawGroup(Groups.unit, entityRuntimeBounds, stats);
        drawDrawGroup(Groups.weather, entityRuntimeBounds, stats);
        drawDrawGroup(Groups.label, entityRuntimeBounds, stats);
        drawDrawGroup(Groups.draw, entityRuntimeBounds, stats);

        drawnDrawEntities.clear();
    }

    private void drawDrawGroup(Iterable<?> group, Rect bounds, SeamWorldDrawStats stats){
        if(group == null){
            return;
        }

        for(Object object : group){
            if(!(object instanceof Drawc draw)){
                continue;
            }

            if(!drawnDrawEntities.add(draw)){
                stats.drawEntityDuplicatesSkipped++;
                continue;
            }

            float clip = draw.clipSize();

            if(!Float.isFinite(clip) || clip <= 0f){
                clip = Vars.tilesize * 2f;
            }

            if(bounds.overlaps(draw.x() - clip / 2f, draw.y() - clip / 2f, clip, clip)){
                draw.draw();
                stats.drawEntitiesQueued++;
            }
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

    private static final class LightTrap implements AutoCloseable{
        private static Field lightsField;
        private static Field circleIndexField;
        private static boolean reflectionReady;
        private static boolean reflectionFailed;
        private static boolean loggedFailure;

        private final LightRenderer lightRenderer;
        private final Seq<?> lights;
        private final int lightsSize;
        private final int circleIndex;
        private final boolean active;

        private LightTrap(){
            this.lightRenderer = null;
            this.lights = null;
            this.lightsSize = 0;
            this.circleIndex = 0;
            this.active = false;
        }

        private LightTrap(LightRenderer lightRenderer, Seq<?> lights, int lightsSize, int circleIndex){
            this.lightRenderer = lightRenderer;
            this.lights = lights;
            this.lightsSize = lightsSize;
            this.circleIndex = circleIndex;
            this.active = true;
        }

        static LightTrap capture(){
            if(Vars.renderer == null || Vars.renderer.lights == null){
                return new LightTrap();
            }

            if(!ensureReflection()){
                return new LightTrap();
            }

            try{
                LightRenderer lightRenderer = Vars.renderer.lights;
                Seq<?> lights = (Seq<?>)lightsField.get(lightRenderer);
                int circleIndex = circleIndexField.getInt(lightRenderer);

                return new LightTrap(lightRenderer, lights, lights.size, circleIndex);
            }catch(Throwable throwable){
                logReflectionFailure(throwable);
                return new LightTrap();
            }
        }

        @Override
        public void close(){
            if(!active){
                return;
            }

            try{
                while(lights.size > lightsSize){
                    lights.pop();
                }

                circleIndexField.setInt(lightRenderer, circleIndex);
            }catch(Throwable throwable){
                logReflectionFailure(throwable);
            }
        }

        private static boolean ensureReflection(){
            if(reflectionReady){
                return true;
            }

            if(reflectionFailed){
                return false;
            }

            try{
                lightsField = LightRenderer.class.getDeclaredField("lights");
                lightsField.setAccessible(true);

                circleIndexField = LightRenderer.class.getDeclaredField("circleIndex");
                circleIndexField.setAccessible(true);

                reflectionReady = true;
                return true;
            }catch(Throwable throwable){
                reflectionFailed = true;
                logReflectionFailure(throwable);
                return false;
            }
        }

        private static void logReflectionFailure(Throwable throwable){
            if(loggedFailure){
                return;
            }

            loggedFailure = true;
            Log.err("[Seam] Failed to guard global LightRenderer queue. Direct subworld light leaks may remain.");
            Log.err(throwable);
        }
    }
}

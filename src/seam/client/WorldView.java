package seam.client;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.graphics.*;
import mindustry.world.blocks.*;
import seam.core.*;
import seam.gen.*;

/** Per-world camera and native renderer parts installed in {@link Core#camera} and {@link Vars#renderer}. */
public final class WorldView{
    private static Binding<Camera> cameras;
    private static Binding<Parts> parts;

    static{
        Scopes.setViewCheck(WorldView::attached);
    }

    /** World rendered by this view. */
    public final Context world;
    /** World camera in world units; starts empty until positioned and sized. */
    public final Camera camera = new Camera();
    private final Mat projection = new Mat(), transform = new Mat(), identity = new Mat();

    private WorldView(Context world){
        this.world = world;
    }

    /** @return whether {@code world} has an attached view */
    public static boolean attached(Context world){
        Scopes.checkThread();
        return cameras != null && cameras.isOwned(world);
    }

    /** Gives a non-headless world its own camera and renderer state, replaying missed load events. Host thread only. */
    public static WorldView attach(Context world){
        Scopes.checkHost();
        Scopes.checkInactive(world);
        if(attached(world)) throw new IllegalStateException("The world already has a view.");
        if(Vars.renderer == null) throw new IllegalStateException("World views need a client renderer.");
        if(world.get(() -> Vars.headless)) throw new IllegalArgumentException("Create displayed worlds with config.headless false.");
        if(cameras == null){
            cameras = new Binding<>(() -> Core.camera, value -> Core.camera = value);
            parts = new Binding<>(Parts::capture, Parts::install);
        }
        int[] registered = new int[2];
        Seq<AddedListener> addedListeners = new Seq<>();
        //constructors register load, map and tile listeners in the installed registry
        Parts owned = world.get(() -> {
            ObjectMap<Object, Integer> beforeSizes = new ObjectMap<>();
            for(var entry : Engine.eventsEvents().copy()) beforeSizes.put(entry.key, entry.value.size);
            registered[0] = beforeSizes.get(ClientLoadEvent.class, 0);
            registered[1] = beforeSizes.get(WorldLoadEvent.class, 0);
            BlockRenderer blocks = null;
            FogRenderer fog = null;
            MinimapRenderer minimap = null;
            OverlayRenderer overlays = null;
            LightRenderer lights = null;
            Parts parts;
            try{
                blocks = new BlockRenderer();
                fog = new FogRenderer();
                minimap = new MinimapRenderer();
                overlays = new OverlayRenderer();
                lights = new LightRenderer();
                parts = new Parts(blocks, fog, minimap, overlays, lights,
                    0f, 0f, 0f, 1f, 0f, null, false, new Color(0f, 0f, 0f, 1f), new Vec2());
            }catch(Throwable error){
                Throwable failure = Scopes.cleanup(error, () -> truncateEvents(beforeSizes));
                var partial = new Parts(blocks, fog, minimap, overlays, lights,
                    0f, 0f, 0f, 1f, 0f, null, false, new Color(0f, 0f, 0f, 1f), new Vec2());
                Scopes.cleanup(failure, partial::release);
                throw error;
            }
            for(var entry : Engine.eventsEvents().copy()){
                int from = beforeSizes.get(entry.key, 0);
                for(int i = from; i < entry.value.size; i++) addedListeners.add(new AddedListener(entry.value, entry.value.get(i)));
            }
            return parts;
        });
        WorldView view = new WorldView(world);
        boolean committed = false, createdInput = false;
        Throwable failure = null;
        try{
            parts.own(world, owned);
            cameras.own(world, view.camera);
            createdInput = WorldPlayer.ensureInput(world);
            //replay load events missed after startup (and map load if a map is already present)
            world.run(() -> {
                ObjectMap<Object, Integer> replayBefore = new ObjectMap<>();
                for(var entry : Engine.eventsEvents().copy()) replayBefore.put(entry.key, entry.value.size);
                try{
                    Subscriptions.replay(ClientLoadEvent.class, registered[0], new ClientLoadEvent());
                    if(Vars.world.width() > 0) Subscriptions.replay(WorldLoadEvent.class, registered[1], new WorldLoadEvent());
                }catch(Throwable error){
                    truncateEvents(replayBefore);
                    throw error;
                }
            });
            world.own(owned::release);
            committed = true;
            return view;
        }catch(Throwable error){
            failure = error;
            throw error;
        }finally{
            if(!committed){
                Throwable cleanup = failure;
                if(createdInput) cleanup = Scopes.cleanup(cleanup, () -> WorldPlayer.releaseInput(world));
                cleanup = Scopes.cleanup(cleanup, () -> cameras.inherit(world));
                cleanup = Scopes.cleanup(cleanup, () -> parts.inherit(world));
                cleanup = Scopes.cleanup(cleanup, () -> {
                    for(int i = addedListeners.size - 1; i >= 0; i--){
                        AddedListener added = addedListeners.get(i);
                        added.bucket.remove(added.listener, true);
                    }
                });
                cleanup = Scopes.cleanup(cleanup, owned::release);
                if(failure == null && cleanup != null){
                    if(cleanup instanceof RuntimeException error) throw error;
                    if(cleanup instanceof Error error) throw error;
                    throw new RuntimeException(cleanup);
                }
            }
        }
    }

    private static void truncateEvents(ObjectMap<Object, Integer> before){
        var events = Engine.eventsEvents();
        for(var entry : events.copy()){
            int keep = before.get(entry.key, -1);
            if(keep < 0){
                events.remove(entry.key);
            }else if(entry.value.size > keep){
                entry.value.truncate(keep);
            }
        }
    }

    private record AddedListener(Seq<Cons<?>> bucket, Cons<?> listener){}

    /** Draws into the bound frame buffer (clearing it first) and restores batch projection, shader, blend and color state. */
    public void draw(){
        world.run(this::drawFrame);
    }

    /** Same as {@link #draw()}, returning false without clearing or drawing if entry would wait for pending work. */
    public boolean tryDraw(){
        return world.tryRun(this::drawFrame);
    }

    private void drawFrame(){
        projection.set(Draw.proj());
        transform.set(Draw.trans());
        Shader shader = Draw.getShader();
        Blending blend = Draw.getBlend();
        float color = Draw.getColorPacked(), mix = Draw.getMixColorPacked(), z = Draw.z(), xscl = Draw.xscl, yscl = Draw.yscl;
        Draw.flush();
        try{
            Draw.trans(identity);
            Draw.shader();
            Draw.blend();
            Vars.renderer.draw();
        }catch(Throwable error){
            Draw.discard();
            Draw.sort(false);
            throw error;
        }finally{
            Draw.proj(projection);
            Draw.trans(transform);
            Draw.shader(shader);
            Draw.blend(blend);
            Draw.reset();
            Draw.color(color);
            Draw.mixcol(mix);
            Draw.z(z);
            Draw.scl(xscl, yscl);
        }
    }

    private static final class Parts{
        final BlockRenderer blocks;
        final FogRenderer fog;
        final MinimapRenderer minimap;
        final OverlayRenderer overlays;
        final LightRenderer lights;
        final float shakeIntensity, shakeReduction, shakeTime, weatherAlpha, landTime;
        final @Nullable LaunchAnimator launchAnimator;
        final boolean launching;
        final Color clearColor;
        final Vec2 camShakeOffset;
        private boolean disposed;

        Parts(BlockRenderer blocks, FogRenderer fog, MinimapRenderer minimap, OverlayRenderer overlays, LightRenderer lights,
              float shakeIntensity, float shakeReduction, float shakeTime, float weatherAlpha, float landTime,
              @Nullable LaunchAnimator launchAnimator, boolean launching, Color clearColor, Vec2 camShakeOffset){
            this.blocks = blocks;
            this.fog = fog;
            this.minimap = minimap;
            this.overlays = overlays;
            this.lights = lights;
            this.shakeIntensity = shakeIntensity;
            this.shakeReduction = shakeReduction;
            this.shakeTime = shakeTime;
            this.weatherAlpha = weatherAlpha;
            this.landTime = landTime;
            this.launchAnimator = launchAnimator;
            this.launching = launching;
            this.clearColor = clearColor;
            this.camShakeOffset = camShakeOffset;
        }

        static Parts capture(){
            var r = Vars.renderer;
            return new Parts(r.blocks, r.fog, r.minimap, r.overlays, r.lights, r.shakeIntensity, r.shakeReduction, r.shakeTime,
                r.weatherAlpha, r.landTime, Engine.rendererLaunchAnimator(r), Engine.rendererLaunching(r),
                Engine.rendererClearColor(r), Engine.rendererCamShakeOffset(r));
        }

        void install(){
            var r = Vars.renderer;
            Engine.rendererSetBlocks(r, blocks);
            Engine.rendererSetFog(r, fog);
            Engine.rendererSetMinimap(r, minimap);
            Engine.rendererSetOverlays(r, overlays);
            Engine.rendererSetLights(r, lights);
            r.shakeIntensity = shakeIntensity;
            r.shakeReduction = shakeReduction;
            r.shakeTime = shakeTime;
            r.weatherAlpha = weatherAlpha;
            r.landTime = landTime;
            Engine.rendererSetLaunchAnimator(r, launchAnimator);
            Engine.rendererSetLaunching(r, launching);
            Engine.rendererSetClearColor(r, clearColor);
            Engine.rendererSetCamShakeOffset(r, camShakeOffset);
        }

        void release(){
            if(disposed) return;
            disposed = true;
            Seq<Disposable> resources = new Seq<>();
            if(blocks != null){
                add(resources, blocks.getShadowBuffer());
                add(resources, Engine.blockRendererDark(blocks));
                Seq<SpriteCache>[] caches = Engine.blockRendererCaches(blocks);
                if(caches != null){
                    for(Seq<SpriteCache> layer : caches){
                        if(layer == null) continue;
                        for(SpriteCache entry : layer) add(resources, entry);
                        layer.clear();
                    }
                }
                if(blocks.floor != null){
                    add(resources, Engine.floorRendererShader(blocks.floor));
                    Mesh[][][] cache = Engine.floorRendererCache(blocks.floor);
                    if(cache != null){
                        for(Mesh[][] col : cache) for(Mesh[] layers : col) for(Mesh mesh : layers) add(resources, mesh);
                    }
                }
            }
            if(fog != null){
                add(resources, Engine.fogRendererStaticFog(fog));
                add(resources, Engine.fogRendererDynamicFog(fog));
            }
            if(lights != null) add(resources, Engine.lightRendererBuffer(lights));
            if(minimap != null){
                add(resources, minimap.getTexture());
                add(resources, minimap.getPixmap());
            }
            Throwable failure = null;
            for(Disposable resource : resources){
                failure = Scopes.cleanup(failure, () -> {
                    if(!(resource instanceof Pixmap pixmap && pixmap.isDisposed())) resource.dispose();
                });
            }
            if(failure instanceof RuntimeException error) throw error;
            if(failure instanceof Error error) throw error;
            if(failure != null) throw new RuntimeException(failure);
        }

        private static void add(Seq<Disposable> out, @Nullable Disposable item){
            if(item != null) out.add(item);
        }
    }
}

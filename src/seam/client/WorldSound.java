package seam.client;

import arc.*;
import arc.audio.*;
import arc.audio.Filters.*;
import arc.func.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.audio.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import seam.core.*;
import seam.gen.*;

/** Per-world looping sounds and ambient pass installed in {@link Control#sound}. Music and UI sounds stay with the host. */
public final class WorldSound extends SoundControl{
    private static final float ambientInterval = 60f / 20f;
    private static final Class<?>[] nativeEvents = {ClientLoadEvent.class, WaveEvent.class, ResetEvent.class};
    private static final Seq<WorldSound> free = new Seq<>();
    private static Binding<SoundControl> binding;
    private static AudioBus worldBus;
    private static int attached;

    private @Nullable Context world;
    private float ambientTime;

    private WorldSound(){
        Engine.soundControlSetSounds(this, new VoiceMap(sounds, true));
    }

    /** @return whether {@code world} has attached audio controls */
    public static boolean attached(Context world){
        Scopes.checkThread();
        return binding != null && binding.isOwned(world);
    }

    /** Gives a non-headless world an isolated bus for looping and ambient sounds. Host thread only. */
    public static WorldSound attach(Context world){
        Scopes.checkHost();
        Scopes.checkInactive(world);
        if(attached(world)) throw new IllegalStateException("The world already has sound.");
        SoundControl host = Vars.control == null ? null : Vars.control.sound;
        if(host == null) throw new IllegalStateException("World sound needs a client sound control.");
        if(world.get(() -> Vars.headless)) throw new IllegalArgumentException("Create displayed worlds with config.headless false.");
        ObjectMap<Sound, Object> hostLoops = Engine.soundControlSounds(host);
        if(!(hostLoops instanceof VoiceMap)) Engine.soundControlSetSounds(host, new VoiceMap(hostLoops, false));
        if(binding == null) binding = new Binding<>(() -> Vars.control.sound, value -> Vars.control.sound = value);
        AudioBus createdBus = attached == 0 ? new AudioBus() : null;
        WorldSound sound = free.isEmpty() ? create(world, host) : free.pop();
        boolean registered = false;
        @SuppressWarnings("unchecked")
        Seq<Cons<?>>[] resetBucket = new Seq[1], updateBucket = new Seq[1];
        Cons<?>[] resetListener = new Cons[1], updateListener = new Cons[1];
        Throwable failure = null;
        try{
            sound.world = world;
            binding.own(world, sound);
            world.run(() -> {
                Seq<Cons<?>> rBucket = Subscriptions.listeners(ResetEvent.class);
                int rBefore = rBucket.size;
                Events.on(ResetEvent.class, e -> { if(sound.world == world) sound.release(); });
                Cons<?> rListener = rBucket.size > rBefore ? rBucket.get(rBefore) : null;
                try{
                    Seq<Cons<?>> uBucket = Subscriptions.listeners(Trigger.update);
                    int uBefore = uBucket.size;
                    Events.run(Trigger.update, () -> { if(sound.world == world) sound.update(); });
                    resetBucket[0] = rBucket;
                    resetListener[0] = rListener;
                    updateBucket[0] = uBucket;
                    if(uBucket.size > uBefore) updateListener[0] = uBucket.get(uBefore);
                }catch(Throwable error){
                    if(rListener != null) rBucket.truncate(rBefore);
                    throw error;
                }
            });
            world.own(() -> {
                try{
                    sound.release();
                    free.add(sound);
                }finally{
                    sound.world = null;
                    if(--attached == 0){
                        var bus = worldBus;
                        worldBus = null;
                        bus.stop();
                        bus.dispose();
                    }
                }
            });
            if(createdBus != null) worldBus = createdBus;
            attached++;
            registered = true;
            return sound;
        }catch(Throwable error){
            failure = error;
            throw error;
        }finally{
            if(!registered){
                Throwable cleanup = Scopes.cleanup(failure, () -> binding.inherit(world));
                cleanup = Scopes.cleanup(cleanup, () -> {
                    if(updateBucket[0] != null && updateListener[0] != null) updateBucket[0].remove(updateListener[0], true);
                    if(resetBucket[0] != null && resetListener[0] != null) resetBucket[0].remove(resetListener[0], true);
                });
                cleanup = Scopes.cleanup(cleanup, sound::release);
                sound.world = null;
                free.add(sound);
                if(createdBus != null){
                    cleanup = Scopes.cleanup(cleanup, () -> {
                        createdBus.stop();
                        createdBus.dispose();
                    });
                }
                if(failure == null && cleanup != null){
                    if(cleanup instanceof RuntimeException error) throw error;
                    if(cleanup instanceof Error error) throw error;
                    throw new RuntimeException(cleanup);
                }
            }
        }
    }

    private static WorldSound create(Context world, SoundControl host){
        WorldSound created = world.get(() -> {
            IntSeq sizes = new IntSeq();
            for(Class<?> type : nativeEvents) sizes.add(Subscriptions.listeners(type).size);
            try{
                return new WorldSound();
            }finally{
                //native listeners load host music, play wave music and stop the whole sound bus on reset
                for(int i = 0; i < nativeEvents.length; i++) Subscriptions.listeners(nativeEvents[i]).truncate(sizes.get(i));
            }
        });
        //constructor allocates a bus and installs a filter on the shared sound bus; restore the host's
        created.uiBus.stop();
        created.uiBus.dispose();
        created.uiBus = host.uiBus;
        AudioFilter hostFilter = Engine.soundControlFilter(host);
        Core.audio.soundBus.setFilter(0, hostFilter);
        Core.audio.soundBus.setFilterParam(0, Filters.paramWet, Vars.state.isGame() && Core.scene != null && Core.scene.hasDialog() ? 1f : 0f);
        created.filter = hostFilter;
        return created;
    }

    /** @return bound context, or null while pooled or disposed */
    public @Nullable Context world(){
        return world;
    }

    @Override
    public void update(){
        if(!Vars.state.isGame()){
            stopVoices();
            return;
        }
        Thread ambient = Engine.soundControlAmbientThread(this);
        if(ambient != null && Vars.state.isPlaying() && (ambientTime += Time.delta) >= ambientInterval){
            ambientTime %= ambientInterval;
            Engine.audioThreadDoLoop(ambient);
        }
        updateLoops();
    }

    @Override
    public void addAmbientSource(AmbientSource source){
        if(Vars.headless) return;
        Thread ambient = Engine.soundControlAmbientThread(this);
        if(ambient == null){
            //native thread type holds pass state; never started
            ambient = Engine.newAudioThread();
            Engine.soundControlSetAmbientThread(this, ambient);
        }
        Engine.audioThreadInputSources(ambient).add(source);
    }

    @Override public void playMusic(@Nullable Music music, boolean interrupt){}
    @Override public void playRandom(){}
    @Override public void stop(){}
    @Override public void keepSilent(){}

    private void release(){
        Engine.soundControlSetAmbientThread(this, null);
        ambientTime = 0f;
        stopVoices();
    }

    private void stopVoices(){
        for(var data : sounds.values()){
            int id = Engine.soundDataSoundID(data);
            if(id > 0) Core.audio.stop(id);
        }
        sounds.clear();
    }

    @SuppressWarnings("unchecked")
    private static class VoiceMap extends ObjectMap<Sound, Object>{
        final boolean world;

        VoiceMap(ObjectMap<Sound, ?> source, boolean world){
            super((ObjectMap<Sound, Object>)source);
            this.world = world;
        }

        @Override
        public void each(Cons2<Sound, Object> mix){
            if(attached == 0){
                super.each(mix);
                return;
            }
            float ambientVolume = Core.settings.getInt("ambientvol", 100) / 100f;
            for(var entry : this){
                Object data = entry.value;
                int id = Engine.soundDataSoundID(data);
                //stop the fading voice before vanilla stops the sound asset and all sibling voices
                if(id > 0 && Mathf.lerpDelta(Engine.soundDataCurVolume(data), Engine.soundDataVolume(data) * ambientVolume, 0.11f) <= 0.001f){
                    Core.audio.stop(id);
                    Engine.soundDataSetSoundID(data, -1);
                }
                Sound sound = entry.key;
                AudioBus previous = sound.bus;
                if(world && previous == Core.audio.soundBus){
                    sound.setBus(worldBus);
                    try{
                        mix.get(sound, data);
                    }finally{
                        sound.setBus(previous);
                    }
                }else{
                    mix.get(sound, data);
                }
            }
        }
    }
}

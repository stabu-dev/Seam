package seam.core;

import arc.*;
import arc.func.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.EventType.*;
import mindustry.game.Saves.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.io.SaveFileReader.*;
import mindustry.logic.*;
import mindustry.world.*;
import mindustry.world.blocks.campaign.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.logic.*;
import mindustry.world.blocks.logic.LogicDisplay.*;
import seam.annotations.Annotations.*;
import seam.gen.*;

import java.lang.reflect.*;
import java.util.*;

@WorldStateSpec(
    allPublicStatic = {Groups.class},
    fields = {
        @FieldGroup(type = Vars.class, names = {"world", "state", "headless", "asyncCore", "unitPhysics", "avoidance", "spawner", "indexer", "pathfinder", "controlPath", "fogControl", "logic"}),
        @FieldGroup(type = Time.class, names = {"time"}),
        @FieldGroup(type = Mathf.class, names = {"rand"}, initial = {"new arc.math.Rand()"})
    },
    payloadHosts = {Tile.class, Building.class, Damage.class, BulletType.class}
)
final class WorldFrame{
    static final Seq<Field> fields = new Seq<>(Field.class);
    static final Seq<Field> contents = new Seq<>(Field.class);
    static final Seq<Runnable> services = new Seq<>();
    //these classes register listeners in static initializers; load them in host scope so child registries stay isolated
    static final Seq<String> listening = Seq.with("mindustry.ai.RtsAI", "mindustry.logic.LExecutor", "mindustry.ui.Menus$MenuDialog", "mindustry.world.blocks.campaign.LandingPad", "mindustry.world.blocks.logic.LogicDisplay");

    private static final byte object = 0, integer = 1, int64 = 2, bool = 3, single = 4, decimal = 5;
    private static final Seq<Field> reflective = new Seq<>(Field.class);
    private static final ObjectMap<Field, Prov<Object>> initial = new ObjectMap<>();
    private static final Seq<Prov<Contents>> copies = new Seq<>();
    private static final Seq<Boolf2<String, CustomChunk>> chunkFilters = new Seq<>();
    private static final byte[] kinds;
    private static float hostSfxVolume;
    private static boolean hasHostSfxVolume;

    static{
        WorldStateGen.registerFields(fields);
        WorldStateGen.registerContents(contents);

        contents(Events.class, "events", ObjectMap<Object, Seq<Cons<?>>>::new, (from, to) -> to.set(from));
        copies.add(() -> new Contents(){
            @Override void capture(){}
            @Override
            void install(){
                if(Core.audio == null) return;
                if(Vars.headless){
                    Core.audio.sfxVolume = 0f;
                }else if(hasHostSfxVolume){
                    Core.audio.sfxVolume = hostSfxVolume;
                }else if(Core.settings != null){
                    Core.audio.sfxVolume = Core.settings.getInt("sfxvol", 100) / 100f;
                }
            }
            @Override void release(){}
        });
        copies.add(Network.EntityIds::new);

        world(Engine.field(Groups.class, "freeQueue"), Seq::new);
        world(Engine.field(Time.class, "runs"), Seq::new);
        world(Engine.field(Time.class, "removal"), Seq::new);
        world(Time.class, "timeRaw");
        world(Pathfinder.class, "wwidth", "wheight");
        world(AvoidanceProcess.class, "wwidth", "wheight");
        world(FogControl.class, "ww", "wh");
        world(Engine.field(RegenProjector.class, "lastUpdateFrame"), () -> -1L);
        contents(RegenProjector.class, "taken", IntSet::new, (from, to) -> {
            to.clear();
            to.addAll(from);
        });
        contents(RegenProjector.class, "mendMap", IntFloatMap::new, (from, to) -> {
            to.clear();
            to.putAll(from);
        });
        world(Engine.field(LandingPad.class, "waiting"), ObjectMap::new);
        world(Engine.field(LandingPad.class, "lastUpdateId"), () -> -1L);
        world(Engine.field(LExecutor.class, "unitTimeouts"), IntFloatMap::new);
        contents(LogicDisplay.class, "displays", () -> new Seq<LogicDisplayBuild>(false), (from, to) -> to.set(from));
        world(Lightning.class, "lastSeed");
        contents(GlobalVars.class, "rand", Rand::new, (from, to) -> {
            to.seed0 = from.seed0;
            to.seed1 = from.seed1;
        });
        world(ControlPathfinder.class, "initialized");
        world(Engine.field(SaveVersion.class, "customChunks"), () -> {
            var copy = new OrderedMap<String, CustomChunk>();
            for(var entry : Engine.saveCustomChunks()){
                if(shouldInheritChunk(entry.key, entry.value)) copy.put(entry.key, entry.value);
            }
            return copy;
        });
        world(Engine.field(SaveFileReader.class, "chunkNested"), () -> false);
        for(String name : new String[]{"byteOutput", "byteOutput2"}){
            Field field = Engine.field(SaveFileReader.class, name);
            ReusableByteOutStream live = Reflect.get(field);
            contents.add(field);
            copies.add(() -> new Contents(){
                byte[] bytes;

                @Override
                void capture(){
                    //only suspended nested chunks need their prefix; idle worlds retain no save-buffer copies
                    bytes = Engine.saveReaderChunkNested() ? live.toByteArray() : null;
                }

                @Override
                void install(){
                    if(bytes != null){
                        live.reset();
                        live.write(bytes, 0, bytes.length);
                    }
                }

                @Override
                void release(){
                    bytes = null;
                }
            });
        }

        kinds = new byte[reflective.size];
        for(int i = 0; i < reflective.size; i++){
            Class<?> type = reflective.items[i].getType();
            kinds[i] = type == int.class ? integer : type == long.class ? int64 : type == boolean.class ? bool : type == float.class ? single : type == double.class ? decimal : object;
            if(type.isPrimitive() && kinds[i] == object) throw new ExceptionInInitializerError("Unsupported world field type: " + reflective.items[i]);
        }

        listening.each(Engine::initType);

        services.addAll(
            //Android R8 merges lambda classes, so reinstall equivalents of the static-initializer listeners directly
            () -> Events.on(ResetEvent.class, e -> {
                Engine.landingPadWaiting().clear();
                Engine.landingPadSetLastUpdateId(-1L);
                Engine.logicExecUnitTimeouts().clear();
                LogicDisplay.displays.clear();
            }),
            () -> Events.on(BuildDamageEvent.class, e -> {
                if(e.build.team.rules().rtsAi){
                    RtsAI ai = e.build.team.data().rtsAi;
                    if(ai != null) Engine.rtsAiDamagedSet(ai).add(e.build);
                }
            }),
            () -> Events.on(DataPatchLoadEvent.class, e -> {
                if(e.assets.any()) throw new IllegalStateException("Worlds cannot load map data assets.");
            })
        );
    }

    static Disposable inheritChunks(Boolf2<String, CustomChunk> filter){
        chunkFilters.add(filter);
        return new Disposable(){
            boolean done;
            @Override public void dispose(){ Context.checkThread(); if(!done){ done = true; chunkFilters.remove(filter, true); } }
            @Override public boolean isDisposed(){ Context.checkThread(); return done; }
        };
    }

    private static boolean shouldInheritChunk(String name, CustomChunk chunk){
        for(Boolf2<String, CustomChunk> filter : chunkFilters){
            if(filter.get(name, chunk)) return true;
        }
        return false;
    }

    final Context owner;
    private final WorldStateGen direct = new WorldStateGen();
    private final Object[] objects = new Object[reflective.size];
    private final long[] primitives = new long[reflective.size];
    private final Contents[] containers = new Contents[copies.size];
    private @Nullable GameState state;
    private @Nullable SaveSlot savesCurrent;
    private boolean savesSaving;
    private float savesTime;
    private long savesPlaytime, savesTimestamp;

    WorldFrame(@Nullable Context owner){
        this.owner = owner;
        for(int i = 0; i < objects.length; i++){
            Prov<Object> value = initial.get(reflective.items[i]);
            if(value == null) continue;
            Object start = value.get();
            switch(kinds[i]){
                case integer, int64 -> primitives[i] = ((Number)start).longValue();
                case bool -> primitives[i] = (Boolean)start ? 1 : 0;
                case single -> primitives[i] = Float.floatToRawIntBits(((Number)start).floatValue());
                case decimal -> primitives[i] = Double.doubleToRawLongBits(((Number)start).doubleValue());
                default -> objects[i] = start;
            }
        }
        for(int i = 0; i < containers.length; i++) containers[i] = copies.get(i).get();
    }

    @Nullable Throwable capture(@Nullable Context other, boolean leaving){
        boolean salvage = owner != null && owner.isInvalid();
        Throwable work = null;
        if(leaving){
            try{
                Workers.drainAsync();
            }catch(Throwable failure){
                work = failure;
            }
        }
        int index = -1;
        try{
            if(!salvage){
                captureNative();
                captureSavesCursor();
            }
            for(index = 0; index < Binding.all.size; index++){
                Binding<?> binding = Binding.all.get(index);
                if(binding.differs(owner, other)) binding.capture(owner, leaving, salvage);
            }
        }catch(Throwable failure){
            for(index++; index < Binding.all.size; index++){
                try{
                    Binding<?> binding = Binding.all.get(index);
                    if(binding.differs(owner, other)) binding.capture(owner, leaving, salvage);
                }catch(Throwable next){
                    if(next != failure) failure.addSuppressed(next);
                }
            }
            if(work != null && work != failure) failure.addSuppressed(work);
            throw failure;
        }
        return work;
    }

    private void captureNative(){
        direct.captureFields();
        try{
            for(int i = 0; i < objects.length; i++){
                Field field = reflective.items[i];
                switch(kinds[i]){
                    case integer -> primitives[i] = field.getInt(null);
                    case int64 -> primitives[i] = field.getLong(null);
                    case bool -> primitives[i] = field.getBoolean(null) ? 1 : 0;
                    case single -> primitives[i] = Float.floatToRawIntBits(field.getFloat(null));
                    case decimal -> primitives[i] = Double.doubleToRawLongBits(field.getDouble(null));
                    default -> objects[i] = field.get(null);
                }
            }
        }catch(IllegalAccessException e){
            throw new IllegalStateException(e);
        }
        direct.capturePayloads();
        state = Vars.state;
        if(owner == null && Core.audio != null){
            hostSfxVolume = Core.audio.sfxVolume;
            hasHostSfxVolume = true;
        }
        for(Contents container : containers) container.capture();
    }

    boolean rejectExposed(){
        boolean exposed = false;
        for(Binding<?> binding : Binding.all) exposed |= binding.rejectExposed(owner);
        return exposed;
    }

    void install(){
        direct.installFields();
        try{
            for(int i = 0; i < objects.length; i++){
                Field field = reflective.items[i];
                switch(kinds[i]){
                    case integer -> field.setInt(null, (int)primitives[i]);
                    case int64 -> field.setLong(null, primitives[i]);
                    case bool -> field.setBoolean(null, primitives[i] != 0);
                    case single -> field.setFloat(null, Float.intBitsToFloat((int)primitives[i]));
                    case decimal -> field.setDouble(null, Double.longBitsToDouble(primitives[i]));
                    default -> field.set(null, objects[i]);
                }
            }
        }catch(IllegalAccessException e){
            throw new IllegalStateException(e);
        }
        direct.installPayloads();

        int index = 0;
        int count = containers.length + Binding.all.size;
        try{
            for(; index < count; index++) installPart(index);
        }catch(Throwable failure){
            for(index++; index < count; index++){
                try{
                    installPart(index);
                }catch(Throwable next){
                    if(next != failure) failure.addSuppressed(next);
                }
            }
            throw failure;
        }
        installSavesCursor();
        if(Vars.logicVars != null && Vars.world != null && Vars.state != null) Vars.logicVars.update();
    }

    private void installPart(int index){
        if(index < containers.length){
            containers[index].install();
        }else{
            Binding.all.get(index - containers.length).install(owner);
        }
    }

    @Nullable GameState state(){
        return owner == Context.current() ? Vars.state : state;
    }

    void resetSavesCursor(){
        savesCurrent = null;
        savesSaving = false;
        savesTime = 0f;
        savesPlaytime = 0L;
        savesTimestamp = 0L;
    }

    private void captureSavesCursor(){
        if(Vars.control == null || Vars.control.saves == null) return;
        Saves saves = Vars.control.saves;
        savesCurrent = Engine.savesCurrent(saves);
        savesSaving = Engine.savesSaving(saves);
        savesTime = Engine.savesTime(saves);
        savesPlaytime = Engine.savesTotalPlaytime(saves);
        savesTimestamp = Engine.savesLastTimestamp(saves);
    }

    private void installSavesCursor(){
        if(Vars.control == null || Vars.control.saves == null) return;
        Saves saves = Vars.control.saves;
        Engine.savesSetCurrent(saves, savesCurrent);
        Engine.savesSetSaving(saves, savesSaving);
        Engine.savesSetTime(saves, savesTime);
        Engine.savesSetTotalPlaytime(saves, savesPlaytime);
        Engine.savesSetLastTimestamp(saves, savesTimestamp);
    }

    void release(){
        direct.release();
        state = null;
        resetSavesCursor();
        Arrays.fill(objects, null);
        for(Contents container : containers) container.release();
        //child disposal must not drop the live host's binding snapshots
        if(owner == null){
            hasHostSfxVolume = false;
            for(Binding<?> binding : Binding.all) binding.release();
        }
    }

    private static void world(Class<?> type, String... names){
        for(String name : names) world(Engine.field(type, name), null);
    }

    private static void world(Field field, @Nullable Prov<Object> value){
        fields.add(field);
        reflective.add(field);
        if(value != null) initial.put(field, value);
    }

    private static <T> void contents(Class<?> type, String name, Prov<T> create, Cons2<T, T> copy){
        Field field = Engine.field(type, name);
        T live = Reflect.get(field), empty = create.get();
        contents.add(field);
        copies.add(() -> new Contents(){
            final T value = create.get();

            @Override void capture(){ copy.get(live, value); }
            @Override void install(){ copy.get(value, live); }
            @Override void release(){ copy.get(empty, value); }
        });
    }

    abstract static class Contents{
        abstract void capture();
        abstract void install();
        abstract void release();
    }
}

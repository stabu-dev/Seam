package seam.compat;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.part.*;
import mindustry.entities.pattern.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.io.SaveFileReader.*;
import mindustry.maps.generators.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;
import mindustry.type.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.consumers.*;
import mindustry.world.draw.*;
import seam.*;
import seam.core.*;
import seam.gen.*;
import seam.io.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

/**
 * Scans enabled third-party Java mods for shared global world state and lets them bridge it per world.
 * Unadapted Java mods holding live world objects in static or singleton fields, unshared save chunks,
 * or global {@link ApplicationListener}s are blocked before child worlds are created until bridged.
 */
public final class Compat{
    private static final ObjectMap<String, Seq<Disposable>> registered = new ObjectMap<>();
    private static final ObjectMap<String, Seq<Disposable>> sharedChunks = new ObjectMap<>();
    private static final ObjectMap<Field, Seq<Disposable>> boundFields = new ObjectMap<>();
    private static Seq<Conflict> cached;
    private static boolean installed, dialogShown;

    static{
        Context.onBeforeCreate(Compat::check);
    }

    private Compat(){}

    /** Describes why an enabled third-party Java mod conflicts with isolated worlds. */
    public record Conflict(String name, String displayName, Seq<String> reasons){}

    /** Hooks startup conflict checks and shows a warning dialog if incompatible Java mods are active. */
    public static void install(){
        Scopes.checkHost();
        if(installed) return;
        installed = true;
        Events.on(ClientLoadEvent.class, e -> onStartup());
        Events.on(ServerLoadEvent.class, e -> onStartup());
    }

    /** Marks {@code modName} as safe without lifecycle hooks; dispose the handle to unregister. */
    public static Disposable register(String modName){
        return register(modName, null, null);
    }

    /**
     * {@code before} runs in the caller before entry (for {@link Binding}); {@code after} runs installed
     * after native services and before {@link Context.Config#after}.
     */
    public static Disposable register(String modName, @Nullable Cons<Context> before, @Nullable Cons<Context> after){
        Scopes.checkHost();
        if(modName == null || modName.trim().isEmpty()) throw new IllegalArgumentException("A mod name is required.");
        String key = normalize(modName);
        Disposable hook = before != null || after != null ? Context.onCreate(before, after) : null;
        Seq<Disposable> entries = registered.get(key, Seq::new);
        Disposable handle = new Disposable(){
            boolean disposed;

            @Override
            public void dispose(){
                Scopes.checkHost();
                if(disposed) return;
                disposed = true;
                if(hook != null) hook.dispose();
                Seq<Disposable> list = registered.get(key);
                if(list != null){
                    list.remove(this, true);
                    if(list.isEmpty()) registered.remove(key);
                }
                cached = null;
            }

            @Override
            public boolean isDisposed(){
                Scopes.checkThread();
                return disposed;
            }
        };
        entries.add(handle);
        cached = null;
        return handle;
    }

    /** Shares a stateless host {@link CustomChunk} (for example content-ID remappers) into every new child world and exempts it from conflict checks. */
    public static Disposable shareChunk(String name){
        Scopes.checkHost();
        if(name == null || name.trim().isEmpty()) throw new IllegalArgumentException("A chunk name is required.");
        Disposable filter = Context.inheritChunks((key, chunk) -> name.equals(key));
        sharedChunks.get(name, Seq::new).add(filter);
        cached = null;
        return new Disposable(){
            boolean disposed;

            @Override
            public void dispose(){
                Scopes.checkHost();
                if(disposed) return;
                disposed = true;
                filter.dispose();
                Seq<Disposable> list = sharedChunks.get(name);
                if(list != null){
                    list.remove(filter, true);
                    if(list.isEmpty()) sharedChunks.remove(name);
                }
                cached = null;
            }

            @Override
            public boolean isDisposed(){
                Scopes.checkThread();
                return disposed;
            }
        };
    }

    /** Isolates a mutable static field per world using {@code initial} for each new context. */
    @SuppressWarnings("unchecked")
    public static <T> Disposable bindField(Class<?> owner, String fieldName, Prov<T> initial){
        Scopes.checkHost();
        if(owner == null || fieldName == null || fieldName.trim().isEmpty() || initial == null){
            throw new IllegalArgumentException("Owner class, field name and initializer are required.");
        }
        Field field = declaredStaticField(owner, fieldName);
        if(Modifier.isFinal(field.getModifiers())){
            throw new IllegalArgumentException("Static field is final (use Compat.bindCollection for final containers): " + owner.getName() + "#" + fieldName);
        }
        Binding<T> binding = new Binding<>(
            () -> {
                try{
                    return (T)field.get(null);
                }catch(IllegalAccessException e){
                    throw new IllegalStateException(e);
                }
            },
            value -> {
                try{
                    field.set(null, value);
                }catch(IllegalAccessException e){
                    throw new IllegalStateException(e);
                }
            }
        );
        Disposable hook = Context.onCreate(world -> binding.own(world, initial.get()), null);
        return registerBoundField(field, combine(hook, binding));
    }

    /** Exchanges the contents of a static container field (Arc or JDK collection/map) per world. */
    public static Disposable bindCollection(Class<?> owner, String fieldName){
        Scopes.checkHost();
        if(owner == null || fieldName == null || fieldName.trim().isEmpty()){
            throw new IllegalArgumentException("Owner class and field name are required.");
        }
        Field field = declaredStaticField(owner, fieldName);
        Object live;
        try{
            live = field.get(null);
        }catch(IllegalAccessException e){
            throw new IllegalStateException(e);
        }
        if(live == null) throw new IllegalArgumentException("Collection field is null: " + owner.getName() + "#" + fieldName);
        ContainerAdapter<Object> adapter = containerAdapter(live);
        if(adapter == null) throw new IllegalArgumentException("Unsupported collection type: " + live.getClass().getName());

        Binding<Object> binding = new Binding<>(
            () -> {
                Object copy = adapter.create.get();
                adapter.copy.get(live, copy);
                return copy;
            },
            state -> adapter.copy.get(state, live)
        );
        Disposable hook = Context.onCreate(world -> binding.own(world, adapter.create.get()), null);
        return registerBoundField(field, combine(hook, binding));
    }

    private static Disposable registerBoundField(Field field, Disposable inner){
        Disposable handle = new Disposable(){
            boolean disposed;

            @Override
            public void dispose(){
                Scopes.checkHost();
                if(disposed) return;
                disposed = true;
                Seq<Disposable> list = boundFields.get(field);
                if(list != null){
                    list.remove(this, true);
                    if(list.isEmpty()) boundFields.remove(field);
                }
                cached = null;
                inner.dispose();
            }

            @Override
            public boolean isDisposed(){
                Scopes.checkThread();
                return disposed;
            }
        };
        boundFields.get(field, Seq::new).add(handle);
        cached = null;
        return handle;
    }

    /** Captures matching host listeners from {@code loader} and attaches them to each new world. */
    public static Disposable shareEvents(ClassLoader loader, Boolf<Object> types){
        Scopes.checkHost();
        if(loader == null || types == null) throw new IllegalArgumentException("A class loader and event filter are required.");
        Boolf2<Object, Cons<?>> filter = (type, listener) -> listener.getClass().getClassLoader() == loader && types.get(type);
        Subscriptions[] captured = {Subscriptions.capture(filter)};
        Disposable hook = Context.onCreate(world -> {
            if(Context.current() == null) captured[0] = Subscriptions.capture(filter);
        }, world -> {
            if(captured[0] != null) captured[0].attach();
        });
        return new Disposable(){
            boolean disposed;

            @Override
            public void dispose(){
                Scopes.checkHost();
                if(disposed) return;
                disposed = true;
                captured[0] = null;
                hook.dispose();
            }

            @Override
            public boolean isDisposed(){
                Scopes.checkThread();
                return disposed;
            }
        };
    }

    /** @return unadapted Java mod conflicts found among currently loaded mods */
    public static Seq<Conflict> conflicts(){
        Scopes.checkThread();
        if(cached == null) cached = scanLoadedMods();
        return cached.copy();
    }

    /** Throws {@link IllegalStateException} if any unadapted Java mod conflict is present. */
    public static void check(){
        Scopes.checkThread();
        Seq<Conflict> found = cached == null ? (cached = scanLoadedMods()) : cached;
        if(found.isEmpty()) return;
        throw new IllegalStateException(format(found));
    }

    private static void onStartup(){
        cached = null;
        Seq<Conflict> found = conflicts();
        if(found.isEmpty()) return;
        Log.warn(format(found));
        if(!Vars.headless && Vars.ui != null && Core.scene != null && !dialogShown){
            dialogShown = true;
            Core.app.post(() -> showDialog(found));
        }
    }

    private static void showDialog(Seq<Conflict> found){
        var dialog = new BaseDialog("Seam: Incompatible Mods");
        dialog.cont.add("The following Java mods keep unscoped world state and are incompatible with Seam.\n" +
            "Disable them in Mods or install a compatibility bridge before playing Seam worlds.").wrap().width(560f).padBottom(12f).row();
        dialog.cont.pane(table -> {
            table.left().defaults().left().pad(4f);
            for(Conflict conflict : found){
                table.add("[accent]" + conflict.displayName + "[] [lightgray](" + conflict.name + ")[]").row();
                for(String reason : conflict.reasons){
                    table.add("  - " + reason).color(Color.lightGray).wrap().width(520f).row();
                }
            }
        }).growX().maxHeight(360f).row();
        dialog.addCloseButton();
        dialog.show();
    }

    private static String format(Seq<Conflict> found){
        var out = new StringBuilder("Incompatible unadapted Java mods detected (disable them or register a Seam Compat bridge):");
        for(Conflict conflict : found){
            out.append("\n  ").append(conflict.displayName).append(" (").append(conflict.name).append("): ");
            for(int i = 0; i < conflict.reasons.size; i++){
                if(i > 0) out.append("; ");
                out.append(conflict.reasons.get(i));
            }
        }
        return out.toString();
    }

    private static Seq<Conflict> scanLoadedMods(){
        Seq<Conflict> result = new Seq<>();
        if(Vars.mods == null) return result;
        OrderedMap<String, CustomChunk> chunks = Engine.saveCustomChunks();
        Seq<ApplicationListener> listeners = Core.app == null ? null : Core.app.getListeners();

        for(LoadedMod mod : Vars.mods.list()){
            if(!mod.enabled() || !mod.isJava() || mod.main == null || mod.loader == null) continue;
            if(isExempt(mod)) continue;
            Seq<String> reasons = inspectMod(mod, chunks, listeners);
            if(reasons.any()){
                String display = mod.meta != null && mod.meta.displayName != null ? mod.meta.displayName : mod.name;
                result.add(new Conflict(mod.name, display, reasons));
            }
        }
        return result;
    }

    private static boolean isExempt(LoadedMod mod){
        if(mod.main instanceof Seam) return true;
        String key = normalize(mod.name);
        if(key.equals("seam") || registered.containsKey(key)) return true;
        if(mod.meta != null){
            if(mod.meta.dependencies != null && mod.meta.dependencies.contains(dep -> normalize(dep).equals("seam"))) return true;
            if(mod.meta.softDependencies != null && mod.meta.softDependencies.contains(dep -> normalize(dep).equals("seam"))) return true;
        }
        return false;
    }

    private static Seq<String> inspectMod(LoadedMod mod, @Nullable OrderedMap<String, CustomChunk> chunks, @Nullable Seq<ApplicationListener> listeners){
        Seq<String> reasons = new Seq<>();
        ClassLoader loader = mod.loader;

        if(chunks != null){
            for(var entry : chunks){
                if(entry.value == null || entry.value instanceof WorldStore || "static-fog-data".equals(entry.key) || sharedChunks.containsKey(entry.key)) continue;
                if(entry.value.getClass().getClassLoader() == loader){
                    reasons.add("custom save chunk '" + entry.key + "'");
                }
            }
        }

        if(listeners != null){
            for(ApplicationListener listener : listeners){
                if(listener != null && listener.getClass().getClassLoader() == loader){
                    reasons.add("global ApplicationListener " + listener.getClass().getName());
                }
            }
        }

        ObjectSet<String> classNames = collectClasses(mod);
        inspectClasses(classNames, mod.main.getClass(), loader, reasons);
        return reasons;
    }

    static void inspectClasses(Iterable<String> classNames, @Nullable Class<?> modMain, ClassLoader loader, Seq<String> reasons){
        Seq<String> sorted = new Seq<>();
        for(String name : classNames) sorted.add(name);
        sorted.sort();
        ObjectSet<String> seen = new ObjectSet<>();

        if(modMain != null){
            inspectInstanceHolder(modMain, modMain.getSimpleName(), loader, 0, new ObjectSet<>(), reasons, seen);
        }

        for(String name : sorted){
            Class<?> type;
            try{
                type = Class.forName(name, false, loader);
            }catch(Throwable ignored){
                continue;
            }
            Field[] declared;
            try{
                declared = type.getDeclaredFields();
            }catch(Throwable ignored){
                continue;
            }
            for(Field field : declared){
                if(!Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || boundFields.containsKey(field)) continue;
                if(holdsWorldState(field)){
                    addReason(reasons, seen, "static world state " + type.getSimpleName() + "#" + field.getName());
                }else{
                    for(Class<?> holder : holderTypes(field, loader)){
                        inspectInstanceHolder(holder, type.getSimpleName() + "#" + field.getName(), loader, 1, new ObjectSet<>(), reasons, seen);
                    }
                }
            }
        }
    }

    private static void inspectInstanceHolder(Class<?> type, String path, ClassLoader loader, int depth, ObjectSet<Class<?>> visited, Seq<String> reasons, ObjectSet<String> seen){
        if(depth > 2) return;
        for(Class<?> cur = type; cur != null && cur != Object.class && cur != Mod.class; cur = cur.getSuperclass()){
            if(depth > 0 && cur.getClassLoader() != loader) break;
            if(!visited.add(cur)) return;
            Field[] declared;
            try{
                declared = cur.getDeclaredFields();
            }catch(Throwable ignored){
                return;
            }
            for(Field field : declared){
                if(Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                String member = path + (depth == 0 ? "#" : " -> " + cur.getSimpleName() + "#") + field.getName();
                if(holdsWorldState(field)){
                    addReason(reasons, seen, (depth == 0 ? "mod singleton state " : "singleton world state ") + member);
                }else if(depth < 2){
                    for(Class<?> next : holderTypes(field, loader)){
                        inspectInstanceHolder(next, member, loader, depth + 1, visited, reasons, seen);
                    }
                }
            }
        }
    }

    private static Seq<Class<?>> holderTypes(Field field, ClassLoader loader){
        var out = new Seq<Class<?>>();
        try{
            collectHolderTypes(field.getGenericType(), loader, 0, out);
        }catch(Throwable ignored){
        }
        if(out.isEmpty()){
            try{
                collectHolderTypes(field.getType(), loader, 0, out);
            }catch(Throwable ignored){
            }
        }
        return out;
    }

    private static void collectHolderTypes(@Nullable Type type, ClassLoader loader, int depth, Seq<Class<?>> out){
        if(type == null || depth > 4) return;
        if(type instanceof Class<?> raw){
            while(raw.isArray()) raw = raw.getComponentType();
            if(raw.isPrimitive() || raw.isInterface() || raw.isEnum() || raw.isAnnotation()) return;
            if(raw.getClassLoader() != loader || isContentDefinition(raw)) return;
            if(!out.contains(raw, true)) out.add(raw);
        }else if(type instanceof ParameterizedType pt){
            collectHolderTypes(pt.getRawType(), loader, depth + 1, out);
            for(Type arg : pt.getActualTypeArguments()) collectHolderTypes(arg, loader, depth + 1, out);
        }else if(type instanceof GenericArrayType arr){
            collectHolderTypes(arr.getGenericComponentType(), loader, depth + 1, out);
        }else if(type instanceof WildcardType wc){
            for(Type bound : wc.getUpperBounds()) collectHolderTypes(bound, loader, depth + 1, out);
            for(Type bound : wc.getLowerBounds()) collectHolderTypes(bound, loader, depth + 1, out);
        }
    }

    private static boolean isContentDefinition(Class<?> type){
        return Content.class.isAssignableFrom(type)
            || BulletType.class.isAssignableFrom(type)
            || Effect.class.isAssignableFrom(type)
            || Ability.class.isAssignableFrom(type)
            || DrawBlock.class.isAssignableFrom(type)
            || DrawPart.class.isAssignableFrom(type)
            || ShootPattern.class.isAssignableFrom(type)
            || Weapon.class.isAssignableFrom(type)
            || Consume.class.isAssignableFrom(type)
            || PlanetGenerator.class.isAssignableFrom(type)
            || BaseGenerator.class.isAssignableFrom(type)
            || AIController.class.isAssignableFrom(type);
    }

    private static void addReason(Seq<String> reasons, ObjectSet<String> seen, String reason){
        if(seen.add(reason)) reasons.add(reason);
    }

    static boolean holdsWorldState(Field field){
        try{
            if(isWorldType(field.getGenericType(), 0)) return true;
        }catch(Throwable ignored){
        }
        try{
            return isWorldClass(field.getType());
        }catch(Throwable ignored){
            return false;
        }
    }

    private static boolean isWorldType(Type type, int depth){
        if(type == null || depth > 4) return false;
        if(type instanceof Class<?> cls) return isWorldClass(cls);
        if(type instanceof ParameterizedType pt){
            if(isWorldType(pt.getRawType(), depth + 1)) return true;
            for(Type arg : pt.getActualTypeArguments()){
                if(isWorldType(arg, depth + 1)) return true;
            }
            return false;
        }
        if(type instanceof GenericArrayType arr) return isWorldType(arr.getGenericComponentType(), depth + 1);
        if(type instanceof WildcardType wc){
            for(Type bound : wc.getUpperBounds()) if(isWorldType(bound, depth + 1)) return true;
            for(Type bound : wc.getLowerBounds()) if(isWorldType(bound, depth + 1)) return true;
        }
        return false;
    }

    private static boolean isWorldClass(Class<?> raw){
        while(raw.isArray()) raw = raw.getComponentType();
        if(raw.isPrimitive() || raw == Object.class) return false;
        return World.class.isAssignableFrom(raw)
            || GameState.class.isAssignableFrom(raw)
            || Tile.class.isAssignableFrom(raw)
            || Building.class.isAssignableFrom(raw)
            || Entityc.class.isAssignableFrom(raw)
            || EntityGroup.class.isAssignableFrom(raw)
            || QuadTree.class.isAssignableFrom(raw)
            || Rules.class.isAssignableFrom(raw)
            || Teams.class.isAssignableFrom(raw)
            || Teams.TeamData.class.isAssignableFrom(raw)
            || PowerGraph.class.isAssignableFrom(raw)
            || WaveSpawner.class.isAssignableFrom(raw)
            || BlockIndexer.class.isAssignableFrom(raw)
            || Pathfinder.class.isAssignableFrom(raw)
            || ControlPathfinder.class.isAssignableFrom(raw)
            || FogControl.class.isAssignableFrom(raw)
            || Logic.class.isAssignableFrom(raw);
    }

    private static ObjectSet<String> collectClasses(LoadedMod mod){
        ObjectSet<String> names = new ObjectSet<>();
        names.add(mod.main.getClass().getName());
        Fi file = mod.file;
        if(file == null) return names;
        if(!file.isDirectory()){
            readArchiveClasses(file.file(), names);
        }else if(mod.root != null){
            walkClasses(mod.root, "", names);
        }
        return names;
    }

    static void readArchiveClasses(File archive, ObjectSet<String> out){
        if(!archive.isFile()) return;
        try(ZipFile zip = new ZipFile(archive)){
            var entries = zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if(name.endsWith(".class") && !name.equals("module-info.class") && !name.startsWith("META-INF/")){
                    out.add(name.substring(0, name.length() - 6).replace('/', '.'));
                }else if(name.endsWith(".dex") && !name.contains("/")){
                    try(InputStream in = zip.getInputStream(entry)){
                        readDexClasses(Streams.copyBytes(in), out);
                    }
                }
            }
        }catch(IOException ignored){
        }
    }

    //reads class_defs descriptors directly from DEX bytes without Android-only APIs
    static void readDexClasses(byte[] dex, ObjectSet<String> out){
        if(dex.length < 112) return;
        if(dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x' || dex[3] != '\n') return;
        ByteBuffer buf = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN);
        int stringIdsSize = buf.getInt(56), stringIdsOff = buf.getInt(60);
        int typeIdsSize = buf.getInt(64), typeIdsOff = buf.getInt(68);
        int classDefsSize = buf.getInt(96), classDefsOff = buf.getInt(100);
        if(stringIdsSize <= 0 || typeIdsSize <= 0 || classDefsSize <= 0) return;
        if(stringIdsOff < 0 || typeIdsOff < 0 || classDefsOff < 0) return;
        if((long)stringIdsOff + (long)stringIdsSize * 4 > dex.length) return;
        if((long)typeIdsOff + (long)typeIdsSize * 4 > dex.length) return;
        if((long)classDefsOff + (long)classDefsSize * 32 > dex.length) return;

        for(int i = 0; i < classDefsSize; i++){
            int classIdx = buf.getInt(classDefsOff + i * 32);
            if(classIdx < 0 || classIdx >= typeIdsSize) continue;
            int descIdx = buf.getInt(typeIdsOff + classIdx * 4);
            if(descIdx < 0 || descIdx >= stringIdsSize) continue;
            int pos = buf.getInt(stringIdsOff + descIdx * 4);
            if(pos < 0 || pos >= dex.length) continue;
            //skip ULEB128 utf16_size prefix
            while(pos < dex.length && (dex[pos++] & 0x80) != 0){}
            int start = pos;
            while(pos < dex.length && dex[pos] != 0) pos++;
            if(pos - start > 2 && dex[start] == 'L' && dex[pos - 1] == ';'){
                String descriptor = new String(dex, start + 1, pos - start - 2, StandardCharsets.UTF_8);
                out.add(descriptor.replace('/', '.'));
            }
        }
    }

    private static void walkClasses(Fi dir, String prefix, ObjectSet<String> out){
        for(Fi child : dir.list()){
            String name = child.name();
            if(child.isDirectory()){
                if(!name.equals("META-INF")) walkClasses(child, prefix.isEmpty() ? name : prefix + "." + name, out);
            }else if(name.endsWith(".class") && !name.equals("module-info.class")){
                String simple = name.substring(0, name.length() - 6);
                out.add(prefix.isEmpty() ? simple : prefix + "." + simple);
            }
        }
    }

    private static Field declaredStaticField(Class<?> owner, String fieldName){
        try{
            Field field = owner.getDeclaredField(fieldName);
            if(!Modifier.isStatic(field.getModifiers())){
                throw new IllegalArgumentException("Field is not static: " + owner.getName() + "#" + fieldName);
            }
            field.setAccessible(true);
            return field;
        }catch(NoSuchFieldException e){
            throw new IllegalArgumentException("Missing static field: " + owner.getName() + "#" + fieldName, e);
        }
    }

    private static Disposable combine(Disposable first, Disposable second){
        return new Disposable(){
            boolean disposed;

            @Override
            public void dispose(){
                Scopes.checkHost();
                if(disposed) return;
                disposed = true;
                Throwable failure = Scopes.cleanup(null, first::dispose);
                failure = Scopes.cleanup(failure, second::dispose);
                if(failure instanceof RuntimeException r) throw r;
                if(failure instanceof Error e) throw e;
                if(failure != null) throw new RuntimeException(failure);
            }

            @Override
            public boolean isDisposed(){
                Scopes.checkThread();
                return disposed;
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable ContainerAdapter<Object> containerAdapter(Object sample){
        if(sample instanceof Seq) return adapter(Seq::new, (Seq a, Seq b) -> b.set(a));
        if(sample instanceof IntSeq) return adapter(IntSeq::new, (IntSeq a, IntSeq b) -> { b.clear(); b.addAll(a); });
        if(sample instanceof FloatSeq) return adapter(FloatSeq::new, (FloatSeq a, FloatSeq b) -> { b.clear(); b.addAll(a); });
        if(sample instanceof LongSeq) return adapter(LongSeq::new, (LongSeq a, LongSeq b) -> { b.clear(); b.addAll(a); });
        if(sample instanceof OrderedSet) return adapter(OrderedSet::new, (OrderedSet a, OrderedSet b) -> { b.clear(); b.addAll(a); });
        if(sample instanceof ObjectSet) return adapter(ObjectSet::new, (ObjectSet a, ObjectSet b) -> { b.clear(); b.addAll(a); });
        if(sample instanceof OrderedMap) return adapter(OrderedMap::new, (OrderedMap a, OrderedMap b) -> { b.clear(); b.putAll(a); });
        if(sample instanceof ObjectMap) return adapter(ObjectMap::new, (ObjectMap a, ObjectMap b) -> { b.clear(); b.putAll(a); });
        if(sample instanceof ObjectIntMap) return adapter(ObjectIntMap::new, (ObjectIntMap a, ObjectIntMap b) -> { b.clear(); b.putAll(a); });
        if(sample instanceof ObjectFloatMap) return adapter(ObjectFloatMap::new, (ObjectFloatMap a, ObjectFloatMap b) -> { b.clear(); b.putAll(a); });
        if(sample instanceof IntSet) return adapter(IntSet::new, (IntSet a, IntSet b) -> { b.clear(); b.addAll(a); });
        if(sample instanceof IntMap) return adapter(IntMap::new, (IntMap a, IntMap b) -> { b.clear(); b.putAll(a); });
        if(sample instanceof IntIntMap) return adapter(IntIntMap::new, (IntIntMap a, IntIntMap b) -> { b.clear(); b.putAll(a); });
        if(sample instanceof IntFloatMap) return adapter(IntFloatMap::new, (IntFloatMap a, IntFloatMap b) -> { b.clear(); b.putAll(a); });
        if(sample instanceof LongMap) return adapter(LongMap::new, (LongMap a, LongMap b) -> { b.clear(); b.putAll(a); });
        if(sample instanceof Bits) return adapter(Bits::new, (Bits a, Bits b) -> { b.clear(); b.or(a); });
        if(sample instanceof List) return adapter(ArrayList::new, (List a, List b) -> { b.clear(); b.addAll(a); });
        if(sample instanceof TreeSet ts) return adapter(() -> new TreeSet(ts.comparator()), (Set a, Set b) -> { b.clear(); b.addAll(a); });
        if(sample instanceof Set) return adapter(LinkedHashSet::new, (Set a, Set b) -> { b.clear(); b.addAll(a); });
        if(sample instanceof TreeMap tm) return adapter(() -> new TreeMap(tm.comparator()), (Map a, Map b) -> { b.clear(); b.putAll(a); });
        if(sample instanceof IdentityHashMap) return adapter(IdentityHashMap::new, (Map a, Map b) -> { b.clear(); b.putAll(a); });
        if(sample instanceof Map) return adapter(LinkedHashMap::new, (Map a, Map b) -> { b.clear(); b.putAll(a); });
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> ContainerAdapter<Object> adapter(Prov<T> create, Cons2<T, T> copy){
        return new ContainerAdapter<>((Prov<Object>)create, (Cons2<Object, Object>)copy);
    }

    private static String normalize(String name){
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private record ContainerAdapter<T>(Prov<T> create, Cons2<T, T> copy){}
}

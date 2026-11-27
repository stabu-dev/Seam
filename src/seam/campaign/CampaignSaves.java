package seam.campaign;

import arc.*;
import arc.assets.*;
import arc.assets.loaders.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.struct.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import seam.client.*;
import seam.core.*;
import seam.gen.*;

/** Per-world or shared native save catalog and preview directory. */
public final class CampaignSaves{
    private static final String previewSuffix = ".spreview";
    private static final ObjectMap<Context, SharedCatalog> catalogs = new ObjectMap<>();
    private static Binding<Directories> directories;
    private static Binding<Saves> catalog;

    private CampaignSaves(){}

    /** Attaches a save directory under {@code directory} without autosaving; requires {@link WorldView}. */
    public static Saves attach(Context world, Fi directory){
        return attach(world, directory, false);
    }

    /** Shares {@code source}'s native save catalog without autosaving on {@code world}; requires {@link WorldView}. Host thread only. */
    public static Saves share(Context world, Context source){
        return share(world, source, false);
    }

    /** Requires an attached {@link WorldView}. When {@code autosave} is true, runs native catalog autosaves on world updates. */
    public static Saves attach(Context world, Fi directory, boolean autosave){
        Scopes.checkHost();
        Scopes.checkInactive(world);
        if(attached(world)) throw new IllegalStateException("The world already has a save catalog.");
        if(directory == null) throw new IllegalArgumentException("A save profile directory is required.");
        if(Vars.control == null || !WorldView.attached(world)) throw new IllegalStateException("Attach a WorldView before the native save catalog.");
        @SuppressWarnings("unchecked")
        AssetLoader<Texture, AssetLoaderParameters<Texture>> previewLoader = Core.assets.getLoader(Texture.class, "save" + previewSuffix);
        if(previewLoader == null) throw new IllegalStateException("Initialize the host save catalog before attaching worlds.");
        if(catalog == null){
            directories = new Binding<>(() -> new Directories(Vars.saveDirectory, Vars.mapPreviewDirectory), paths -> {
                Vars.saveDirectory = paths.saves;
                Vars.mapPreviewDirectory = paths.previews;
            });
            catalog = new Binding<>(() -> Vars.control.saves, saves -> Vars.control.saves = saves);
        }
        Saves saves;
        try{
            //the native constructor installs a preview loader shared by all catalogs
            saves = world.get(Saves::new);
        }finally{
            Core.assets.setLoader(Texture.class, previewSuffix, previewLoader);
        }
        Fi previews = directory.child("previews");
        SharedCatalog shared = new SharedCatalog(saves, previews);
        shared.refs = 1;
        @SuppressWarnings("unchecked")
        Seq<Cons<?>>[] updateBucket = new Seq[1];
        Cons<?>[] updateListener = new Cons[1];
        boolean committed = false;
        boolean[] active = {false};
        Throwable failure = null;
        try{
            if(autosave){
                world.run(() -> {
                    Seq<Cons<?>> bucket = Subscriptions.listeners(Trigger.update);
                    int before = bucket.size;
                    Events.run(Trigger.update, saves::update);
                    updateBucket[0] = bucket;
                    if(bucket.size > before) updateListener[0] = bucket.get(before);
                });
            }
            directories.own(world, new Directories(directory.child("saves"), previews));
            catalog.own(world, saves);
            Scopes.resetSavesCursor(world);
            catalogs.put(world, shared);
            world.own(() -> {
                if(active[0]){
                    if(catalogs.get(world) == shared) catalogs.remove(world);
                    shared.release();
                }
            });
            if(!WorldPlayer.attached(world)) WorldPlayer.attach(world);
            active[0] = true;
            committed = true;
            return saves;
        }catch(Throwable error){
            failure = error;
            throw error;
        }finally{
            if(!committed){
                if(catalogs.get(world) == shared) catalogs.remove(world);
                Throwable cleanup = Scopes.cleanup(failure, () -> Scopes.resetSavesCursor(world));
                cleanup = Scopes.cleanup(cleanup, () -> catalog.inherit(world));
                cleanup = Scopes.cleanup(cleanup, () -> directories.inherit(world));
                cleanup = Scopes.cleanup(cleanup, () -> {
                    if(updateBucket[0] != null && updateListener[0] != null) updateBucket[0].remove(updateListener[0], true);
                });
                if(failure == null && cleanup != null){
                    if(cleanup instanceof RuntimeException error) throw error;
                    if(cleanup instanceof Error error) throw error;
                    throw new RuntimeException(cleanup);
                }
            }
        }
    }

    /** Shares {@code source}'s native save catalog while keeping {@code world}'s active slot and autosave cursor isolated; requires {@link WorldView}. */
    public static Saves share(Context world, Context source, boolean autosave){
        Scopes.checkHost();
        Scopes.checkInactive(world);
        Scopes.checkUsable(world);
        if(source == null) throw new IllegalArgumentException("A source world is required.");
        Scopes.checkUsable(source);
        if(!attached(source)) throw new IllegalStateException("The source world has no attached save catalog.");
        if(attached(world)) throw new IllegalStateException("The world already has a save catalog.");
        if(Vars.control == null || !WorldView.attached(world)) throw new IllegalStateException("Attach a WorldView before the native save catalog.");
        SharedCatalog shared = catalogs.get(source);
        @SuppressWarnings("unchecked")
        Seq<Cons<?>>[] updateBucket = new Seq[1];
        Cons<?>[] updateListener = new Cons[1];
        boolean counted = false, committed = false;
        boolean[] active = {false};
        Throwable failure = null;
        try{
            if(autosave){
                world.run(() -> {
                    Seq<Cons<?>> bucket = Subscriptions.listeners(Trigger.update);
                    int before = bucket.size;
                    Events.run(Trigger.update, shared.saves::update);
                    updateBucket[0] = bucket;
                    if(bucket.size > before) updateListener[0] = bucket.get(before);
                });
            }
            directories.share(world, source);
            catalog.share(world, source);
            Scopes.resetSavesCursor(world);
            shared.refs++;
            counted = true;
            catalogs.put(world, shared);
            world.own(() -> {
                if(active[0]){
                    if(catalogs.get(world) == shared) catalogs.remove(world);
                    shared.release();
                }
            });
            if(!WorldPlayer.attached(world)) WorldPlayer.attach(world);
            active[0] = true;
            committed = true;
            return shared.saves;
        }catch(Throwable error){
            failure = error;
            throw error;
        }finally{
            if(!committed){
                if(catalogs.get(world) == shared) catalogs.remove(world);
                if(counted) shared.refs--;
                Throwable cleanup = Scopes.cleanup(failure, () -> Scopes.resetSavesCursor(world));
                cleanup = Scopes.cleanup(cleanup, () -> catalog.inherit(world));
                cleanup = Scopes.cleanup(cleanup, () -> directories.inherit(world));
                cleanup = Scopes.cleanup(cleanup, () -> {
                    if(updateBucket[0] != null && updateListener[0] != null) updateBucket[0].remove(updateListener[0], true);
                });
                if(failure == null && cleanup != null){
                    if(cleanup instanceof RuntimeException error) throw error;
                    if(cleanup instanceof Error error) throw error;
                    throw new RuntimeException(cleanup);
                }
            }
        }
    }

    private static void releasePreviews(Fi directory){
        var assets = Core.assets;
        synchronized(assets){
            var names = new ObjectSet<String>();
            for(String name : assets.getAssetNames()) if(ownsPreview(directory, name)) names.add(name);
            //Arc cannot enumerate queued descriptors publicly, and an import can delete their preview files
            for(AssetDescriptor<?> entry : Engine.assetsLoadQueue(assets)){
                if(ownsPreview(directory, entry.fileName)) names.add(entry.fileName);
            }
            AssetDescriptor<?> active = assets.getCurrentLoading();
            if(active != null && ownsPreview(directory, active.fileName)) names.add(active.fileName);
            for(String name : names) assets.unload(name);
        }
    }

    private static boolean ownsPreview(Fi directory, String name){
        return name.endsWith(previewSuffix) && directory.equals(new Fi(name).parent());
    }

    /** @return whether {@code world} has an attached save catalog */
    public static boolean attached(Context world){
        Scopes.checkThread();
        return catalog != null && catalog.isOwned(world) && WorldPlayer.attached(world);
    }

    private static final class SharedCatalog{
        final Saves saves;
        final Fi previews;
        int refs;

        SharedCatalog(Saves saves, Fi previews){
            this.saves = saves;
            this.previews = previews;
        }

        void release(){
            if(--refs == 0) releasePreviews(previews);
        }
    }

    private record Directories(Fi saves, Fi previews){}
}

package seam.io;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.struct.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.io.*;
import mindustry.type.*;
import seam.core.*;
import seam.gen.*;

import java.io.*;
import java.util.zip.*;

/** Uncompressed per-world MSAV serialization and pre-validated host save loading. */
public final class WorldIO{
    private WorldIO(){}

    /** Writes the current world to uncompressed MSAV bytes with default {@link SaveOptions}. */
    public static byte[] write(){
        return write(new SaveOptions());
    }

    /** Writes the installed world as uncompressed MSAV bytes. */
    public static byte[] write(SaveOptions options){
        Scopes.checkState();
        if(options == null) throw new IllegalArgumentException("Save options are required.");
        var bytes = new ByteArrayOutputStream();
        SaveIO.write(bytes, options);
        return bytes.toByteArray();
    }

    /** Reads uncompressed save bytes into a headless world with default settings. */
    public static Context read(byte[] bytes){
        return read(bytes, config -> {});
    }

    /** Reads uncompressed save bytes into a headless world customized by {@code configure}. */
    public static Context read(byte[] bytes, Cons<Context.Config> configure){
        return read(bytes, configure, false);
    }

    /**
     * Reads uncompressed save bytes into a fresh context. When {@code applyView} is false (default), host camera, player
     * and control groups stay intact; when true, defaults {@code config.headless} to false and applies saved view metadata.
     */
    public static Context read(byte[] bytes, Cons<Context.Config> configure, boolean applyView){
        Scopes.checkState();
        if(bytes == null || configure == null) throw new IllegalArgumentException("Save data and configuration are required.");
        return new Context(config -> {
            config.headless = !applyView;
            configure.get(config);
            Cons<Context> after = config.after;
            config.after = world -> {
                load(bytes, applyView, () -> {
                    if(Vars.logicVars != null) Vars.logicVars.update();
                    after.get(world);
                    if(Vars.logicVars != null) Vars.logicVars.update();
                    Events.fire(new SaveLoadEvent(false));
                    if(!Vars.state.isCampaign()) Events.fire(new RulesLoadEvent(Vars.state.rules, true));
                });
            };
        });
    }

    /** Validates compressed header, metadata and zlib stream before calling native {@link SaveIO#load}. Host scope only. */
    public static void loadHost(Fi file){
        Scopes.checkState();
        if(Context.current() != null) throw new IllegalStateException("Load a host save outside world scopes.");
        if(file == null) throw new IllegalArgumentException("A host save file is required.");
        byte[] bytes;
        try{
            bytes = file.readBytes();
            try(var input = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))){
                SaveIO.readHeader(input);
                SaveVersion version = SaveIO.getSaveWriter(input.readInt());
                if(version == null) throw new IOException("Unknown native save version.");
                version.getMeta(input);
                byte[] buffer = new byte[8192];
                //read through the zlib checksum before native loading resets the live world
                while(input.read(buffer) != -1){}
            }
        }catch(IOException | RuntimeException error){
            throw new SaveIO.SaveException(error);
        }
        SaveIO.load(new InflaterInputStream(new ByteArrayInputStream(bytes)), Vars.world.context);
    }

    private static void load(byte[] bytes, boolean applyView, Runnable after){
        ContentLoader content = Vars.content;
        MappableContent[][] previousMapper = Engine.contentTemporaryMapper(content);
        Reads reader = Engine.saveReaderChunkReads();
        DataInput previousInput = reader.input;
        Camera camera = Core.camera;
        Player player = Vars.player;
        InputHandler controls = !Vars.headless && Vars.control != null ? Vars.control.input : null;
        if(!Vars.headless && controls == null) throw new IllegalStateException("Initialize client controls before reading a world.");
        UnitType controlled = controls == null ? null : controls.controlledType;
        IntSeq[] groups = controls == null ? null : controls.controlGroups;
        SaveIO.SaveException failure = null;
        try(CounterInputStream counter = new CounterInputStream(new ByteArrayInputStream(bytes)); var stream = new DataInputStream(counter)){
            if(!applyView && !Vars.headless){
                Core.camera = new Camera();
                Vars.player = Player.create();
            }
            SaveIO.readHeader(stream);
            SaveVersion version = SaveIO.getSaveWriter(stream.readInt());
            if(version == null) throw new IOException("Unknown native save version.");
            version.read(stream, counter, new SaveReadState(Vars.world.context));
            if(stream.read() != -1) throw new IOException("Trailing native save data.");
            after.run();
        }catch(Throwable error){
            failure = new SaveIO.SaveException(error);
            throw failure;
        }finally{
            Throwable cleanup = Scopes.cleanup(failure, () -> Vars.world.setGenerating(false));
            cleanup = Scopes.cleanup(cleanup, () -> content.setTemporaryMapper(previousMapper));
            reader.input = previousInput;
            if(!applyView){
                Core.camera = camera;
                Vars.player = player;
                if(controls != null){
                    controls.controlledType = controlled;
                    controls.controlGroups = groups;
                }
            }
            if(failure == null && cleanup != null) throw new SaveIO.SaveException(cleanup);
        }
    }
}

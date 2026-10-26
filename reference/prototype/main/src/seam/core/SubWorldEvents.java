package seam.core;

import java.lang.reflect.*;
import arc.*;
import arc.func.*;
import arc.struct.*;

/** Isolates {@link Events} listeners so subworld systems do not touch host listeners. */
@SuppressWarnings("unchecked")
public class SubWorldEvents{
    private static final Field eventsField;

    static{
        try{
            eventsField = Events.class.getDeclaredField("events");
            eventsField.setAccessible(true);
        }catch(Throwable t){
            throw new RuntimeException("Failed to access Events map", t);
        }
    }

    private final ObjectMap<Object, Seq<Cons<?>>> local = new ObjectMap<>();
    private ObjectMap<Object, Seq<Cons<?>>> host;
    private boolean active;

    public void begin(){
        if(active) return;

        ObjectMap<Object, Seq<Cons<?>>> global = global();
        host = new ObjectMap<>();
        host.putAll(global);

        global.clear();
        global.putAll(local);
        active = true;
    }

    public void end(){
        if(!active) return;

        try{
            ObjectMap<Object, Seq<Cons<?>>> global = global();
            local.clear();
            local.putAll(global);

            global.clear();
            if(host != null) global.putAll(host);
        }finally{
            host = null;
            active = false;
        }
    }

    public void dispose(){
        if(active) end();
        local.clear();
        host = null;
    }

    private static ObjectMap<Object, Seq<Cons<?>>> global(){
        try{
            return (ObjectMap<Object, Seq<Cons<?>>>)eventsField.get(null);
        }catch(Throwable t){
            throw new RuntimeException("Failed to read Events map", t);
        }
    }
}
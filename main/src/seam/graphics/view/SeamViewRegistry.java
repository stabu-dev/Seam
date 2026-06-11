package seam.graphics.view;

import seam.world.*;
import arc.struct.*;

public final class SeamViewRegistry{
    private final IntMap<SeamView> byId = new IntMap<>();
    private final Seq<SeamView> views = new Seq<>();

    public SeamView create(int id, int runtimeId, String name, SeamProjection projection){
        if(byId.containsKey(id)){
            throw new IllegalArgumentException("View id already exists: " + id);
        }

        SeamView view = new SeamView(id, runtimeId, name, projection);

        byId.put(id, view);
        views.add(view);

        return view;
    }

    public SeamView add(SeamView view){
        if(view == null){
            throw new NullPointerException("view");
        }

        if(byId.containsKey(view.id())){
            throw new IllegalArgumentException("View id already exists: " + view.id());
        }

        byId.put(view.id(), view);
        views.add(view);

        return view;
    }

    public SeamView get(int id){
        return byId.get(id);
    }

    public boolean contains(int id){
        return byId.containsKey(id);
    }

    public Seq<SeamView> all(){
        return views.copy();
    }

    public boolean remove(int id){
        SeamView view = byId.remove(id);

        if(view == null){
            return false;
        }

        views.remove(view, true);

        return true;
    }

    public void removeRuntime(int runtimeId){
        Seq<SeamView> copy = views.copy();

        for(SeamView view : copy){
            if(view.runtimeId() == runtimeId){
                remove(view.id());
            }
        }
    }

    public void clear(){
        byId.clear();
        views.clear();
    }
}
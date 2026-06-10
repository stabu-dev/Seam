package mindustry.gen;

import mindustry.entities.*;

public final class SeamGroupSetHelper{
    public static final EntityIndexer all = (e, pos) -> {
        if(e instanceof IndexableEntity__all ix) ix.setIndex__all(pos);
    };
    public static final EntityIndexer player = (e, pos) -> {
        if(e instanceof IndexableEntity__player ix) ix.setIndex__player(pos);
    };
    public static final EntityIndexer bullet = (e, pos) -> {
        if(e instanceof IndexableEntity__bullet ix) ix.setIndex__bullet(pos);
    };
    public static final EntityIndexer unit = (e, pos) -> {
        if(e instanceof IndexableEntity__unit ix) ix.setIndex__unit(pos);
    };
    public static final EntityIndexer build = (e, pos) -> {
        if(e instanceof IndexableEntity__build ix) ix.setIndex__build(pos);
    };
    public static final EntityIndexer sync = (e, pos) -> {
        if(e instanceof IndexableEntity__sync ix) ix.setIndex__sync(pos);
    };
    public static final EntityIndexer draw = (e, pos) -> {
        if(e instanceof IndexableEntity__draw ix) ix.setIndex__draw(pos);
    };
    public static final EntityIndexer fire = (e, pos) -> {
        if(e instanceof IndexableEntity__fire ix) ix.setIndex__fire(pos);
    };
    public static final EntityIndexer puddle = (e, pos) -> {
        if(e instanceof IndexableEntity__puddle ix) ix.setIndex__puddle(pos);
    };
    public static final EntityIndexer weather = (e, pos) -> {
        if(e instanceof IndexableEntity__weather ix) ix.setIndex__weather(pos);
    };
    public static final EntityIndexer label = (e, pos) -> {
        if(e instanceof IndexableEntity__label ix) ix.setIndex__label(pos);
    };
    public static final EntityIndexer powerGraph = (e, pos) -> {
        if(e instanceof IndexableEntity__powerGraph ix) ix.setIndex__powerGraph(pos);
    };
}

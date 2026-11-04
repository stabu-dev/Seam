package seam;

import arc.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.*;
import org.junit.*;

import static org.junit.Assert.*;

/** Native behavior which a world-isolation boundary must account for. */
public class VanillaWorldTest{
    @Before
    public void before(){
        Events.clear();
        Groups.init();
    }

    @After
    public void after(){
        Groups.clear();
        Groups.updatePooling();
        Events.clear();
        Vars.world = null;
    }

    @Test
    public void separateTileGridsStillShareNativeEntityGroups(){
        World a = new World(), b = new World();
        a.tiles = new Tiles(3, 4);
        b.tiles = new Tiles(5, 2);
        Vars.world = a;
        EffectState first = EffectState.create();
        first.add();
        Vars.world = b;
        EffectState second = EffectState.create();
        second.add();
        assertNotSame(a.tiles, b.tiles);
        assertEquals(2, Groups.effect.size());
        assertTrue(Groups.effect.contains(e -> e == first));
        assertTrue(Groups.effect.contains(e -> e == second));
        Vars.world = a;
        assertEquals(2, Groups.effect.size());
    }

    @Test
    public void worldConstructorsRegisterListenersInTheSameEventBus(){
        World a = new World(), b = new World();
        int first = a.tileChanges, second = b.tileChanges;
        Vars.world = a;
        Events.fire(new TileChangeEvent());
        assertEquals(first + 1, a.tileChanges);
        assertEquals(second + 1, b.tileChanges);
    }
}

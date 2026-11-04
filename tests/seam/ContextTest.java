package seam;

import arc.*;
import arc.func.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.net.*;
import org.junit.*;

import static org.junit.Assert.*;

public class ContextTest{
    @Before
    public void before(){
        Events.clear();
        Groups.init();
        Vars.world = null;
        Vars.state = new GameState();
    }

    @After
    public void after(){
        assertNull(Context.current());
        Groups.clear();
        Groups.updatePooling();
        Events.clear();
        Time.clear();
        Time.setInternalTime(0);
        Vars.world = null;
        Vars.state = null;
        Vars.net = null;
    }

    @Test
    public void ownsWorldStateAndEntityGroups(){
        Context a = new Context(), b = new Context();
        World hostWorld = Vars.world;
        GameState hostState = Vars.state;
        EntityGroup<?> hostUnits = Groups.unit;
        a.run(() -> {
            assertNotSame(hostWorld, Vars.world);
            assertNotSame(hostState, Vars.state);
            EntityGroup<?> aUnits = Groups.unit;
            a.run(() -> assertSame(aUnits, Groups.unit));
            b.run(() -> {
                assertNotSame(aUnits, Groups.unit);
                assertNotSame(Vars.world, hostWorld);
            });
            assertSame(aUnits, Groups.unit);
        });
        assertSame(hostWorld, Vars.world);
        assertSame(hostState, Vars.state);
        assertSame(hostUnits, Groups.unit);
    }

    @Test
    public void everyPublicEntityGroupReferenceIsContextLocal() throws Exception{
        Context child = new Context();
        for(var field : Groups.class.getFields()){
            if(!EntityGroup.class.isAssignableFrom(field.getType())) continue;
            Object hostGroup = field.get(null);
            child.run(() -> {
                try{
                    assertNotSame(field.getName(), hostGroup, field.get(null));
                }catch(IllegalAccessException error){
                    throw new AssertionError(error);
                }
            });
            assertSame(hostGroup, field.get(null));
        }
    }

    @Test
    public void localListenersOnlyReceiveLocalEvents(){
        Context a = new Context(), b = new Context();
        int[] counts = new int[2];
        a.run(() -> Events.on(String.class, e -> counts[0]++));
        b.run(() -> Events.on(String.class, e -> counts[1]++));
        a.run(() -> Events.fire("a"));
        assertArrayEquals(new int[]{1, 0}, counts);
        b.run(() -> Events.fire("b"));
        assertArrayEquals(new int[]{1, 1}, counts);
    }

    @Test
    public void unawareHostListenersAreNotRoutedYet(){
        int[] count = {0};
        Events.on(String.class, e -> count[0]++);
        Context child = new Context();
        child.run(() -> Events.fire("child"));
        assertEquals(0, count[0]);
        Events.fire("host");
        assertEquals(1, count[0]);
    }

    @Test
    public void nestedAndRecursiveSwitchesRestoreLatestAssignments(){
        Context a = new Context(), b = new Context();
        GameState host = Vars.state;
        GameState assigned = new GameState();
        a.run(() -> {
            Vars.state = assigned;
            b.run(() -> {
                GameState other = Vars.state;
                a.run(() -> {
                    assertSame(a, Context.current());
                    assertSame(assigned, Vars.state);
                    Vars.state = new GameState();
                });
                assertSame(other, Vars.state);
                assertSame(b, Context.current());
            });
            assertNotSame(assigned, Vars.state);
            Vars.state = assigned;
            assertSame(assigned, Vars.state);
            a.run(() -> assertSame(a, Context.current()));
            assertSame(assigned, Vars.state);
        });
        assertNull(Context.current());
        assertSame(host, Vars.state);
        a.run(() -> assertSame(assigned, Vars.state));
        assertSame(host, Vars.state);
    }

    @Test
    public void thrownErrorRestoresHostAndPreservesIdentity(){
        Context a = new Context(), b = new Context();
        GameState host = Vars.state;
        EntityGroup<?> hostUnits = Groups.unit;
        AssertionError error = new AssertionError("sentinel");
        assertSame(error, assertThrows(AssertionError.class, () -> a.run(() -> {
            World first = Vars.world;
            b.run(() -> {
                a.run(() -> assertSame(first, Vars.world));
                throw error;
            });
        })));
        assertNull(Context.current());
        assertSame(host, Vars.state);
        assertSame(hostUnits, Groups.unit);
    }

    @Test
    public void eventListenersCanBeAddedAndRemovedNatively(){
        Context child = new Context();
        int[] count = {0};
        Cons<String> listener = e -> count[0]++;
        child.run(() -> {
            Events.on(String.class, listener);
            Events.fire("added");
            assertTrue(Events.remove(String.class, listener));
            Events.fire("removed");
        });
        assertEquals(1, count[0]);
    }

    @Test
    public void constructionInsideAnotherContextRestoresItsRegistrations(){
        Context parent = new Context();
        parent.run(() -> {
            World world = Vars.world;
            EntityGroup<?> units = Groups.unit;
            int[] count = {0};
            Events.on(String.class, e -> count[0]++);
            Context child = new Context();
            assertSame(parent, Context.current());
            assertSame(world, Vars.world);
            assertSame(units, Groups.unit);
            child.run(() -> assertNotSame(world, Vars.world));
            Events.fire("parent");
            assertEquals(1, count[0]);
        });
    }

    @Test
    public void nestedNativeDispatchPreservesTheOriginalExceptionAndListeners(){
        Context a = new Context(), b = new Context();
        RuntimeException failure = new RuntimeException("listener");
        int[] calls = {0};
        a.run(() -> Events.on(String.class, e -> {
            calls[0]++;
            b.run(() -> Events.fire(e));
        }));
        b.run(() -> Events.on(String.class, e -> { throw failure; }));
        for(int i = 0; i < 2; i++){
            assertSame(failure, assertThrows(RuntimeException.class, () -> a.run(() -> Events.fire("test"))));
            assertNull(Context.current());
        }
        assertEquals(2, calls[0]);
    }

    @Test
    public void clearingLocalListenersLeavesOtherContextsIntact(){
        int[] calls = {0, 0};
        Events.on(String.class, e -> calls[0]++);
        Context a = new Context(), b = new Context();
        b.run(() -> Events.on(String.class, e -> calls[1]++));
        a.run(Events::clear);
        a.run(() -> Events.fire("local"));
        assertArrayEquals(new int[]{0, 0}, calls);
        b.run(() -> Events.fire("other"));
        Events.fire("host");
        assertArrayEquals(new int[]{1, 1}, calls);
    }

    @Test
    public void entityGroupsAndClearingFlagAreScoped(){
        Context a = new Context(), b = new Context();
        EffectState effect = EffectState.create();
        a.run(() -> {
            effect.add();
            assertEquals(1, Groups.effect.size());
            Groups.isClearing = true;
            b.run(() -> {
                assertEquals(0, Groups.effect.size());
                assertFalse(Groups.isClearing);
            });
            assertEquals(1, Groups.effect.size());
            assertTrue(Groups.isClearing);
        });
        assertEquals(0, Groups.effect.size());
    }

    @Test
    public void wrongThreadIsRejected() throws InterruptedException{
        Context child = new Context();
        Throwable[] thrown = {null};
        Thread thread = new Thread(() -> {
            try{
                child.run(() -> {});
            }catch(Throwable error){
                thrown[0] = error;
            }
        });
        thread.start();
        thread.join(5000);
        assertFalse(thread.isAlive());
        assertTrue(thrown[0] instanceof IllegalStateException);
    }

    @Test
    public void nullTasksAreRejectedBeforeExchangingState(){
        Context child = new Context();
        EventKey key = new EventKey();
        Events.run(key, () -> {});
        key.onHash = () -> fail("A null task entered state exchange.");
        assertThrows(IllegalArgumentException.class, () -> child.run(null));
        assertThrows(IllegalArgumentException.class, () -> child.get(null));
        assertNull(Context.current());
    }

    @Test
    public void repeatedSwitchesDoNotLeakContext(){
        Context a = new Context(), b = new Context();
        for(int i = 0; i < 1000; i++){
            a.run(() -> b.run(() -> assertSame(b, Context.current())));
            assertNull(Context.current());
        }
    }

    @Test
    public void eventMapCopyCannotReenterAStateExchange(){
        Context child = new Context();
        GameState host = Vars.state;
        EventKey key = new EventKey();
        Events.run(key, () -> {});
        boolean[] attempted = {false};
        key.onHash = () -> {
            attempted[0] = true;
            child.run(() -> fail("Reentered an incomplete exchange."));
        };
        assertThrows(IllegalStateException.class, () -> child.run(() -> fail("Entered after capture failed.")));
        assertTrue(attempted[0]);
        assertSame(host, Vars.state);
        assertNull(Context.current());
        child.run(() -> assertSame(child, Context.current()));
    }

    @Test
    public void captureFailureRemainsAttachedToAnyOriginalThrowable(){
        Throwable[] errors = {new RuntimeException("task"), new AssertionError("task"), new Exception("task")};
        GameState host = Vars.state;
        for(Throwable expected : errors){
            Context child = new Context();
            EventKey key = new EventKey();
            RuntimeException capture = new RuntimeException("capture");
            Throwable actual = assertThrows(Throwable.class, () -> child.run(() -> {
                Events.run(key, () -> {});
                key.failure = capture;
                ContextTest.<RuntimeException>raise(expected);
            }));
            assertSame(expected, actual);
            assertArrayEquals(new Throwable[]{capture}, actual.getSuppressed());
            assertSame(host, Vars.state);
            assertNull(Context.current());
        }
    }

    @Test
    public void incompleteInstallationRestoresTheHostAndAllowsRetry(){
        Context child = new Context();
        GameState host = Vars.state;
        EntityGroup<?> units = Groups.unit;
        World world = child.get(() -> Vars.world);
        EventKey key = new EventKey();
        int[] count = {0};
        child.run(() -> {
            Events.run(key, () -> {});
            Events.on(String.class, e -> count[0]++);
        });
        RuntimeException failure = new RuntimeException("installation");
        key.failure = failure;
        assertSame(failure, assertThrows(RuntimeException.class, () -> child.run(() -> fail("Incomplete state was installed."))));
        assertSame(host, Vars.state);
        assertSame(units, Groups.unit);
        assertNull(Context.current());
        key.failure = null;
        child.run(() -> {
            assertSame(world, Vars.world);
            Events.fire("retry");
        });
        assertEquals(1, count[0]);
    }

    @Test
    public void failedCaptureMakesOnlyThatContextUnusable(){
        Context child = new Context();
        EventKey key = new EventKey();
        child.run(() -> Events.run(key, () -> {}));
        RuntimeException failure = new RuntimeException("capture");
        assertSame(failure, assertThrows(RuntimeException.class, () -> child.run(() -> key.failure = failure)));
        key.failure = null;
        assertThrows(IllegalStateException.class, () -> child.run(() -> fail("Reused an incomplete snapshot.")));
        assertNull(Context.current());
        new Context().run(() -> {});
    }

    @Test
    public void anInvalidAncestorDoesNotPreventRestorationOfTheHost(){
        Context a = new Context(), b = new Context();
        GameState host = Vars.state;
        EventKey key = new EventKey();
        a.run(() -> Events.run(key, () -> {}));
        RuntimeException failure = new RuntimeException("capture");
        assertThrows(IllegalStateException.class, () -> a.run(() -> b.run(() -> {
            assertSame(failure, assertThrows(RuntimeException.class, () -> a.run(() -> key.failure = failure)));
        })));
        key.failure = null;
        assertSame(host, Vars.state);
        assertNull(Context.current());
        assertThrows(IllegalStateException.class, () -> a.run(() -> {}));
        assertThrows(IllegalStateException.class, () -> b.run(() -> {}));
        new Context().run(() -> {});
    }

    @Test
    public void skippedRestorationInvalidatesTheExposedContext(){
        Context a = new Context(), b = new Context(), healthy = new Context();
        GameState host = Vars.state, exposed = b.get(() -> Vars.state);
        EventKey key = new EventKey();
        a.run(() -> Events.run(key, () -> {}));
        assertThrows(IllegalStateException.class, () -> a.run(() -> {
            try{
                b.run(() -> {
                    RuntimeException capture = new RuntimeException("capture");
                    assertSame(capture, assertThrows(RuntimeException.class, () -> a.run(() -> key.failure = capture)));
                });
                fail("Restored an invalid ancestor.");
            }catch(IllegalStateException expected){
                assertSame(a, Context.current());
                assertSame(exposed, Vars.state);
                Vars.state.wave = 99;
            }
        }));
        assertSame(host, Vars.state);
        assertThrows(IllegalStateException.class, () -> b.run(() -> fail("Reused exposed data.")));
        healthy.run(() -> assertSame(healthy, Context.current()));
    }

    @Test
    public void caughtNestedFailureCannotTurnIntoASuccessfulResult(){
        for(boolean recursive : new boolean[]{false, true}){
            Context a = new Context(), b = new Context();
            EventKey key = new EventKey();
            a.run(() -> Events.run(key, () -> {}));
            RuntimeException failure = new RuntimeException("restore");
            Prov<String> task = () -> {
                assertSame(failure, assertThrows(RuntimeException.class, () -> b.run(() -> key.failure = failure)));
                return "invalid success";
            };
            assertThrows(IllegalStateException.class, () -> a.get(() -> recursive ? a.get(task) : task.get()));
            key.failure = null;
            assertNull(Context.current());
            b.run(() -> assertSame(b, Context.current()));
        }
    }

    @Test
    public void failedAncestorRestorationDoesNotPermitReuse(){
        GameState host = Vars.state;
        for(boolean once : new boolean[]{false, true}){
            Context a = new Context(), b = new Context();
            EventKey key = new EventKey();
            a.run(() -> Events.run(key, () -> {}));
            RuntimeException failure = new RuntimeException("restore");
            assertSame(failure, assertThrows(RuntimeException.class, () -> a.run(() -> b.run(() -> {
                if(once){
                    key.onHash = () -> { throw failure; };
                }else{
                    key.failure = failure;
                }
            }))));
            key.failure = null;
            assertSame(host, Vars.state);
            assertNull(Context.current());
            assertThrows(IllegalStateException.class, () -> a.run(() -> fail("Reused a partially restored ancestor.")));
            b.run(() -> assertSame(b, Context.current()));
        }
    }

    @Test
    public void failedEntryCaptureLeavesTheActiveContextUsable(){
        Context a = new Context(), b = new Context();
        EventKey key = new EventKey();
        int[] count = {0};
        a.run(() -> {
            Events.run(key, () -> {});
            Events.on(String.class, e -> count[0]++);
        });
        RuntimeException failure = new RuntimeException("entry capture");
        a.run(() -> {
            World world = Vars.world;
            key.onHash = () -> { throw failure; };
            assertSame(failure, assertThrows(RuntimeException.class, () -> b.run(() -> fail("Entered after incomplete capture."))));
            assertSame(a, Context.current());
            assertSame(world, Vars.world);
            b.run(() -> assertSame(b, Context.current()));
            Events.fire("active");
            assertEquals(1, count[0]);
        });
        a.run(() -> assertSame(a, Context.current()));
    }

    @Test
    public void deferredPoolingQueuesStayWithTheirContext(){
        Context a = new Context(), b = new Context();
        EffectState host = EffectState.create();
        Object marker = new Object();
        host.data = marker;
        host.add();
        host.remove();
        EffectState first = a.get(() -> {
            EffectState effect = EffectState.create();
            effect.data = marker;
            effect.add();
            effect.remove();
            return effect;
        });
        b.run(Groups::updatePooling);
        assertSame(marker, host.data);
        assertSame(marker, first.data);
        a.run(Groups::updatePooling);
        assertNull(first.data);
        assertSame(marker, host.data);
        Groups.updatePooling();
        assertNull(host.data);
    }

    @Test
    public void delayedTasksAndClocksBelongToTheirContext(){
        Context a = new Context(), b = new Context();
        int[] runs = new int[3];
        Time.setInternalTime(5);
        Time.run(10f, () -> runs[0]++);
        a.run(() -> Time.run(10f, () -> runs[1]++));
        b.run(() -> Time.run(10f, () -> runs[2]++));
        a.run(() -> advance(20f));
        assertArrayEquals(new int[]{0, 1, 0}, runs);
        assertEquals(5f, Time.time, 0f);
        assertEquals(20f, a.get(() -> Time.time), 0f);
        assertEquals(0f, b.get(() -> Time.time), 0f);
        advance(20f);
        assertArrayEquals(new int[]{1, 1, 0}, runs);
    }

    @Test
    public void nestedClockUpdatesKeepTheOuterRemovalQueue(){
        Context a = new Context(), b = new Context();
        int[] runs = new int[3];
        b.run(() -> Time.run(0f, () -> runs[2]++));
        a.run(() -> {
            Time.run(0f, () -> runs[0]++);
            Time.run(0f, () -> {
                runs[1]++;
                b.run(() -> advance(1f));
            });
            advance(1f);
            assertTrue(Time.getRuns().isEmpty());
        });
        assertArrayEquals(new int[]{1, 1, 1}, runs);
        assertTrue(b.get(() -> Time.getRuns().isEmpty()));
    }

    @Test
    public void worldServicesBelongToTheirContext(){
        Context a = new Context(), b = new Context();
        Object[] host = services(), first = a.get(ContextTest::services), second = b.get(ContextTest::services);
        for(int i = 0; i < host.length; i++){
            assertNotNull(first[i]);
            assertNotSame(host[i], first[i]);
            assertNotSame(first[i], second[i]);
        }
        assertArrayEquals(host, services());
        a.run(() -> assertArrayEquals(first, services()));
    }

    @Test
    public void updateRejectsUnscopedFogAndNetwork(){
        Context child = new Context();
        Vars.net = new Net(null);
        child.run(() -> Vars.state.rules.fog = true);
        assertThrows(IllegalStateException.class, () -> child.update(1f));
        child.run(() -> Vars.state.rules.fog = false);
        Vars.net.setClientConnected();
        assertThrows(IllegalStateException.class, () -> child.update(1f));
        assertNull(Context.current());
    }

    private static void advance(float delta){
        float previous = Time.delta;
        Time.delta = delta;
        Time.update();
        Time.delta = previous;
    }

    private static Object[] services(){
        return new Object[]{Vars.logic, Vars.spawner, Vars.indexer, Vars.asyncCore, Vars.unitPhysics, Vars.avoidance};
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void raise(Throwable error) throws E{
        throw (E)error;
    }

    private static class EventKey{
        RuntimeException failure;
        Runnable onHash;

        @Override
        public int hashCode(){
            if(onHash != null){
                Runnable task = onHash;
                onHash = null;
                task.run();
            }
            if(failure != null) throw failure;
            return 42;
        }
    }
}

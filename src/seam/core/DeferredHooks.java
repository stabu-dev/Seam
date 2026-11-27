package seam.core;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import seam.gen.*;

import java.lang.reflect.*;
import java.util.concurrent.*;

final class DeferredHooks{
    private static Application installed;
    private static Object installedTimerThread;
    private static final ObjectSet<Tracked> pending = new ObjectSet<>();
    private static final Queue<Submission> staged = new Queue<>();
    private static final ThreadLocal<Tracked> running = new ThreadLocal<>();
    private static final Object hostScope = new Object();
    private static int hostSuspensions;

    static void suspendHost(){
        synchronized(pending){ hostSuspensions++; }
    }

    static void resumeHost(boolean restored){
        synchronized(pending){
            if(--hostSuspensions != 0) return;
        }
        while(true){
            Submission submission;
            synchronized(pending){
                if(hostSuspensions != 0 || staged.isEmpty()) return;
                submission = staged.removeFirst();
                if(restored) pending.add(submission.task);
            }
            try{
                if(restored){
                    submission.executor.launch(submission.task);
                }else{
                    cancel(submission.task);
                }
            }catch(RuntimeException | Error error){
                if(submission.task.task instanceof Future<?> future){
                    future.cancel(false);
                }else{
                    Log.err(error);
                }
            }finally{
                try{
                    submission.executor.dispatched();
                }catch(RuntimeException | Error error){
                    Log.err(error);
                }
            }
        }
    }

    private static void cancel(Tracked tracked){
        if(tracked.task instanceof Future<?> future) future.cancel(false);
        tracked.finish();
    }

    private record Submission(Executor executor, Tracked task){}

    static ExecutorService executor(ExecutorService source){
        return source == null || source instanceof Executor ? source : new Executor(source);
    }

    static void install(){
        Context.checkThread();
        Timer host = Timer.instance();
        if(installed == Core.app && installedTimerThread == Engine.timerThread()){
            executors();
            return;
        }
        Application app = Core.app;
        Field field = pendingField(app.getClass());
        Object queue = Reflect.get(app, field);
        Seq<Runnable> source = queue instanceof TaskQueue taskQueue ? Engine.taskQueueRunnables(taskQueue) : Reflect.get(app, field);
        if(source.getClass() != Seq.class && !(source instanceof ApplicationQueue)){
            throw new IllegalStateException("Native application queue is already replaced: " + source.getClass());
        }
        synchronized(Engine.timerThreadLock()){
            Object thread = Engine.timerThread();
            Seq<Timer> timers = Engine.timerThreadInstances(thread);
            if(timers.getClass() != Seq.class && !(timers instanceof Timers)){
                throw new IllegalStateException("Native timer registry is already replaced: " + timers.getClass());
            }
            for(Timer timer : timers) timer(timer);
            if(!(host instanceof RoutingTimer)){
                timer(host);
                Engine.timerThreadSetInstance(thread, new RoutingTimer(host));
            }
            if(!(timers instanceof Timers)) Engine.timerThreadSetInstances(thread, new Timers(timers));
            installedTimerThread = thread;
        }
        synchronized(source){
            if(!(source instanceof ApplicationQueue)){
                if(queue instanceof TaskQueue taskQueue){
                    Engine.taskQueueSetRunnables(taskQueue, new ApplicationQueue(source));
                }else{
                    Reflect.set(app, field, new ApplicationQueue(source));
                }
            }
        }
        installed = app;
        executors();
    }

    private static void executors(){
        ExecutorService main = Vars.mainExecutor, core = Core.executor;
        Vars.mainExecutor = executor(main);
        Core.executor = core == main ? Vars.mainExecutor : executor(core);
    }

    static void complete(@Nullable Context scope){
        Object tag = scope == null ? hostScope : scope;
        Thread self = Thread.currentThread();
        Seq<Tracked> current = new Seq<>();
        boolean interrupted = false;
        try{
            while(true){
                current.clear();
                synchronized(pending){
                    for(Tracked task : pending){
                        if(task.scope == tag && task.runner != self) current.add(task);
                    }
                }
                if(current.isEmpty()) return;
                for(Tracked task : current){
                    while(true){
                        try{
                            task.done.await();
                            break;
                        }catch(InterruptedException error){
                            interrupted = true;
                        }
                    }
                }
            }
        }finally{
            if(interrupted) Thread.currentThread().interrupt();
        }
    }

    static boolean idle(@Nullable Context scope){
        Object tag = scope == null ? hostScope : scope;
        Thread self = Thread.currentThread();
        synchronized(pending){
            for(Tracked task : pending){
                if(task.scope == tag && task.runner != self) return false;
            }
            return true;
        }
    }

    static void cancel(Timer timer, Deferred.TimerApplication owner){
        synchronized(timer){
            Seq<Timer.Task> current = Engine.timerTasks(timer);
            for(int i = current.size - 1; i >= 0; i--) cancel(current.get(i), owner);
        }
    }

    static void cancel(Timer.Task task, Deferred.TimerApplication owner){
        while(true){
            Timer timer = Engine.timerTaskTimer(task);
            if(timer == null){
                synchronized(task){
                    if(Engine.timerTaskTimer(task) != null) continue;
                    if(Engine.timerTaskApp(task) == owner) Engine.timerTaskSetApp(task, owner.backend);
                    return;
                }
            }
            synchronized(timer){
                synchronized(task){
                    if(Engine.timerTaskTimer(task) != timer) continue;
                    if(Engine.timerTaskApp(task) == owner){
                        task.cancel();
                        Engine.timerTaskSetApp(task, owner.backend);
                    }
                    return;
                }
            }
        }
    }

    private static Timer.Task canceled(Timer.Task task){
        synchronized(task){
            if(task.isScheduled()) throw new IllegalArgumentException("The same task may not be scheduled twice.");
            task.cancel();
            return task;
        }
    }

    private static Field pendingField(Class<?> type){
        for(Class<?> current = type; current != null; current = current.getSuperclass()){
            try{
                Field field = current.getDeclaredField("runnables");
                field.setAccessible(true);
                return field;
            }catch(NoSuchFieldException ignored){
            }
        }
        throw new IllegalStateException("Native application queue is unavailable: " + type.getName());
    }

    static void timer(Timer timer){
        synchronized(timer){
            Seq<Timer.Task> source = Engine.timerTasks(timer);
            if(source instanceof Tasks) return;
            if(source.getClass() != Seq.class) throw new IllegalStateException("Native task queue is already replaced: " + source.getClass());
            Engine.timerSetTasks(timer, new Tasks(source, timer));
        }
    }

    private static final class ApplicationQueue extends Seq<Runnable>{
        ApplicationQueue(Seq<Runnable> source){
            super(source);
        }

        @Override
        public synchronized Seq<Runnable> add(Runnable task){
            Deferred owner = Deferred.current();
            return super.add(owner == null || task instanceof Deferred.Job ? task : owner.wrap(task));
        }
    }

    private static final class Timers extends Seq<Timer>{
        Timers(Seq<Timer> source){
            super(source);
        }

        @Override
        public Seq<Timer> add(Timer value){
            timer(value);
            return super.add(value);
        }
    }

    private static final class Tasks extends Seq<Timer.Task>{
        final Timer timer;

        Tasks(Seq<Timer.Task> source, Timer timer){
            super(source);
            this.timer = timer;
        }

        @Override
        public Seq<Timer.Task> add(Timer.Task task){
            try{
                Deferred owner = Deferred.current();
                Application app = Engine.timerTaskApp(task);
                if(app instanceof Deferred.TimerApplication bound) app = bound.backend;
                Application target = owner == null ? app : owner.timer(timer, app);
                if(target == null){
                    task.cancel();
                    return this;
                }
                Engine.timerTaskSetApp(task, target);
                return super.add(task);
            }catch(Throwable error){
                task.cancel();
                throw error;
            }
        }
    }

    private static final class RoutingTimer extends Timer{
        final Timer host;

        RoutingTimer(Timer host){
            this.host = host;
        }

        private Timer target(){
            Deferred owner = Deferred.current();
            return owner == null ? host : owner.defaultTimer();
        }

        @Override
        public Task scheduleTask(Task task, float delay, float interval, int repeats){
            Timer target = target();
            return target == null ? canceled(task) : target.scheduleTask(task, delay, interval, repeats);
        }

        @Override public void clear(){ Timer timer = target(); if(timer != null) timer.clear(); }
        @Override public void stop(){ if(host != null){ Timer timer = target(); if(timer != null) timer.stop(); } }
        @Override public void start(){ if(host != null){ Timer timer = target(); if(timer != null) timer.start(); } }
        @Override public boolean isEmpty(){ Timer timer = target(); return timer == null || timer.isEmpty(); }
        @Override public void delay(long millis){ Timer timer = target(); if(timer != null) timer.delay(millis); }
    }

    private static final class Executor extends AbstractExecutorService{
        final ExecutorService delegate;
        private int held;
        private boolean closing;

        Executor(ExecutorService delegate){
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable task){
            Tracked tracked = new Tracked(task, scope());
            synchronized(pending){
                if(closing || delegate.isShutdown()) throw new RejectedExecutionException("The executor is shut down.");
                Tracked parent = running.get();
                if(hostSuspensions != 0 && tracked.scope == hostScope && (parent == null || parent.scope != hostScope)){
                    staged.addLast(new Submission(this, tracked));
                    held++;
                    return;
                }
                pending.add(tracked);
            }
            launch(tracked);
        }

        void launch(Tracked tracked){
            try{
                delegate.execute(tracked);
            }catch(RuntimeException | Error error){
                tracked.finish();
                throw error;
            }
        }

        void dispatched(){
            boolean finish;
            synchronized(pending){ finish = --held == 0 && closing; }
            if(finish) delegate.shutdown();
        }

        @Override
        public void shutdown(){
            synchronized(pending){
                closing = true;
                if(held != 0) return;
            }
            delegate.shutdown();
        }

        @Override
        public java.util.List<Runnable> shutdownNow(){
            java.util.List<Runnable> submitted = new java.util.ArrayList<>();
            synchronized(pending){
                closing = true;
                for(int i = 0; i < staged.size;){
                    Submission submission = staged.get(i);
                    if(submission.executor != this){
                        i++;
                    }else{
                        staged.removeIndex(i);
                        held--;
                        submission.task.finish();
                        submitted.add(submission.task.task);
                    }
                }
            }
            java.util.List<Runnable> removed = delegate.shutdownNow();
            for(Runnable task : removed){
                if(task instanceof Tracked tracked){
                    tracked.finish();
                    submitted.add(tracked.task);
                }else{
                    submitted.add(task);
                }
            }
            return submitted;
        }

        @Override
        public boolean isShutdown(){
            synchronized(pending){ return closing || delegate.isShutdown(); }
        }

        @Override
        public boolean isTerminated(){
            synchronized(pending){
                if(held != 0) return false;
            }
            return delegate.isTerminated();
        }

        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException{ return delegate.awaitTermination(timeout, unit); }
    }

    private static Object scope(){
        Context owner;
        if(Context.onThread()){
            owner = Context.current();
        }else{
            Deferred label = Deferred.current();
            owner = label == null ? null : label.context;
            Tracked parent = running.get();
            if(owner == null && parent != null) return parent.scope;
        }
        return owner == null ? hostScope : owner;
    }

    private static final class Tracked implements Runnable{
        final Runnable task, body;
        final Object scope;
        final CountDownLatch done = new CountDownLatch(1);
        volatile Thread runner;

        Tracked(Runnable task, Object scope){
            this.task = task;
            this.scope = scope;
            body = Deferred.backgroundCurrent(task);
        }

        @Override
        public void run(){
            Tracked previous = running.get();
            runner = Thread.currentThread();
            running.set(this);
            try{
                body.run();
            }finally{
                if(previous == null){
                    running.remove();
                }else{
                    running.set(previous);
                }
                runner = null;
                finish();
            }
        }

        void finish(){
            synchronized(pending){ pending.remove(this); }
            done.countDown();
        }
    }
}

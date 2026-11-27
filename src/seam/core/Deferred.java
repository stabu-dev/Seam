package seam.core;

import arc.*;
import arc.struct.*;
import arc.util.*;

import java.net.*;

/** Tracks application posts and Timer tasks owned by one context; queued work is canceled when disposed. */
public class Deferred implements Disposable, AutoCloseable{
    private static final ThreadLocal<Deferred> worker = new ThreadLocal<>();
    private static final Runnable canceled = () -> {};

    volatile Context context;
    private final ObjectSet<Job> pending = new ObjectSet<>();
    private final Seq<Timer> timers = new Seq<>();
    private Queue<Job> waiting;
    private TimerApplication application;
    private Timer defaultTimer;

    /** Constructed only by {@link Context.Config#deferred}, once per context. */
    public Deferred(Context context){
        context.admit(this);
        this.context = context;
        if(Core.app != null) DeferredHooks.install();
    }

    /** Enrolls an existing Timer, including one stopped before hook installation. */
    public static void enroll(Timer timer){
        Context.checkState();
        if(timer == null) throw new IllegalArgumentException("A Timer is required.");
        DeferredHooks.install();
        DeferredHooks.timer(timer);
    }

    static Deferred current(){
        if(Context.onThread()){
            Context current = Context.current();
            if(current != null) return current.deferred;
        }
        return worker.get();
    }

    static @Nullable Context workerContext(){
        Deferred owner = worker.get();
        return owner == null ? null : owner.context;
    }

    /** Wraps a single-use main-thread callback; closing this owner cancels it without running. */
    public synchronized Runnable wrap(Runnable task){
        if(task == null) throw new IllegalArgumentException("A callback is required.");
        if(context == null) return canceled;
        Job job = new Job(this, task);
        pending.add(job);
        return job;
    }

    /** Labels background work so later posts or Timer schedulings belong to this owner. Does not install globals. */
    public Runnable background(Runnable task){
        if(task == null) throw new IllegalArgumentException("A callback is required.");
        return () -> {
            Deferred previous = worker.get();
            worker.set(this);
            try{
                task.run();
            }finally{
                if(previous == null){
                    worker.remove();
                }else{
                    worker.set(previous);
                }
            }
        };
    }

    static Runnable backgroundCurrent(Runnable task){
        Deferred current = current();
        return current == null ? task : current.background(task);
    }

    synchronized Application timer(Timer timer, Application backend){
        if(context == null) return null;
        if(application == null) application = new TimerApplication(this, backend);
        if(application.backend != backend) throw new IllegalStateException("Timer scheduling changed physical applications.");
        if(!timers.contains(timer, true)) timers.add(timer);
        return application;
    }

    Timer defaultTimer(){
        synchronized(this){
            if(context == null) return null;
            if(defaultTimer != null) return defaultTimer;
        }
        Timer created = new Timer(), result;
        synchronized(this){
            if(context == null){
                result = null;
            }else{
                if(defaultTimer == null){
                    defaultTimer = created;
                    timers.add(created);
                }
                result = defaultTimer;
            }
        }
        if(result != created) created.stop();
        return result;
    }

    @Override
    public boolean isDisposed(){
        return context == null;
    }

    @Override
    public void dispose(){
        Context.checkThread();
        Seq<Timer> previous;
        Timer privateTimer;
        synchronized(this){
            if(context == null) return;
            context = null;
            for(Job job : pending) job.task = null;
            pending.clear();
            waiting = null;
            previous = new Seq<>(timers);
            timers.clear();
            privateTimer = defaultTimer;
            defaultTimer = null;
        }
        Throwable failure = null;
        for(Timer timer : previous){
            failure = Context.cleanup(failure, () -> DeferredHooks.cancel(timer, application));
        }
        if(privateTimer != null) failure = Context.cleanup(failure, privateTimer::stop);
        if(failure instanceof RuntimeException error) throw error;
        if(failure instanceof Error error) throw error;
        if(failure != null) throw new RuntimeException(failure);
    }

    @Override
    public void close(){
        dispose();
    }

    static final class Job implements Runnable{
        final Deferred owner;
        Runnable task;
        boolean queued;

        Job(Deferred owner, Runnable task){
            this.owner = owner;
            this.task = task;
        }

        @Override
        public void run(){
            Context.checkThread();
            Context context;
            synchronized(owner){
                if(task == null) return;
                context = owner.context;
                if(owner.waiting != null && owner.waiting.first() != this){
                    if(!queued){
                        owner.waiting.addLast(this);
                        queued = true;
                    }
                    return;
                }
            }
            try{
                if(context == null || !context.canRun()){
                    consume();
                }else if(!context.tryRun(() -> {
                    Runnable body = consume();
                    if(body != null) body.run();
                })){
                    synchronized(owner){
                        if(!queued){
                            owner.waiting = new Queue<>();
                            owner.waiting.addLast(this);
                            queued = true;
                        }
                    }
                    //Arc drains a separate snapshot of the queue, so host callbacks still run this frame
                    Core.app.post(this);
                }
            }catch(Throwable failure){
                consume();
                throw failure;
            }
        }

        private Runnable consume(){
            Runnable body;
            Job next = null;
            synchronized(owner){
                owner.pending.remove(this);
                body = task;
                task = null;
                if(queued && owner.waiting != null && owner.waiting.first() == this){
                    owner.waiting.removeFirst();
                    if(owner.waiting.isEmpty()){
                        owner.waiting = null;
                    }else{
                        next = owner.waiting.first();
                    }
                }
            }
            if(next != null) Core.app.post(next);
            return body;
        }
    }

    //only Timer.Task.app receives this dispatcher; Core.app keeps the physical backend (required by Android Activity cast)
    static final class TimerApplication implements Application{
        final Deferred owner;
        final Application backend;

        TimerApplication(Deferred owner, Application backend){
            this.owner = owner;
            this.backend = backend;
        }

        @Override
        public void post(Runnable task){
            if(owner.isDisposed() && task instanceof Timer.Task timer){
                backend.post(() -> DeferredHooks.cancel(timer, this));
            }else{
                backend.post(owner.wrap(task));
            }
        }
        @Override public Seq<ApplicationListener> getListeners(){ return backend.getListeners(); }
        @Override public ApplicationType getType(){ return backend.getType(); }
        @Override public int getVersion(){ return backend.getVersion(); }
        @Override public long getJavaHeap(){ return backend.getJavaHeap(); }
        @Override public long getNativeHeap(){ return backend.getNativeHeap(); }
        @Override public Thread getMainThread(){ return backend.getMainThread(); }
        @Override public String getClipboardText(){ return backend.getClipboardText(); }
        @Override public void setClipboardText(String text){ backend.setClipboardText(text); }
        @Override public boolean openFolder(String file){ return backend.openFolder(file); }
        @Override public boolean openURI(String uri){ return backend.openURI(uri); }
        @Override public void getDnsServers(Seq<InetSocketAddress> out){ backend.getDnsServers(out); }
        @Override public void exit(){ backend.exit(); }
        @Override public void dispose(){ backend.dispose(); }
    }
}

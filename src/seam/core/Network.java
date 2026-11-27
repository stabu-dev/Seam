package seam.core;

import arc.*;
import arc.func.*;
import arc.net.Server.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.net.*;
import mindustry.net.Net.*;
import seam.annotations.Annotations.*;
import seam.gen.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;

@NetFacade(
    target = Net.class,
    className = "LocalNetBase",
    offlineFalse = {"active", "server", "client"},
    offlineNull = {"getCurrentStream", "getConnectFilter"},
    offlineEmpty = {"getConnections"},
    offlineDrop = {"send", "sendExcept"}
)
final class Network implements NetProvider{
    private static volatile @Nullable byte[] serverData;
    private static long described;

    final NetProvider delegate;

    private Network(NetProvider delegate){
        this.delegate = delegate;
    }

    static void install(){
        Net endpoint = Vars.net instanceof LocalNet routed ? routed.host() : Vars.net;
        if(endpoint == null || !endpoint.server()) return;
        NetProvider current = Engine.netProvider(endpoint);
        if(current instanceof Network) return;
        Engine.netSetProvider(endpoint, new Network(current));
        ArcNetProvider sockets = sockets(current);
        if(sockets == null) return;
        describe();
        Engine.arcNetServer(sockets).setDiscoveryHandler((address, handler) -> {
            byte[] data = serverData;
            if(data != null) handler.respond(ByteBuffer.wrap(data));
        });
        Core.app.addListener(new ApplicationListener(){
            @Override
            public void update(){
                if(Vars.net.server() && Time.timeSinceMillis(described) >= 1000) describe();
            }
        });
    }

    static void enableLocalSimulation(){
        if(Vars.net != null && !(Vars.net instanceof LocalNet)) Vars.net = new LocalNet(Vars.net);
    }

    static void updateWorldLogic(Runnable step){
        Administration admins = Vars.netServer == null ? null : Vars.netServer.admins;
        if(admins == null || !Engine.adminsModified(admins)){
            step.run();
            return;
        }
        Engine.adminsSetModified(admins, false);
        try{
            step.run();
        }finally{
            Engine.adminsSetModified(admins, true);
        }
    }

    private static final class LocalNet extends LocalNetBase{
        LocalNet(Net host){
            super(host);
            Engine.netPingExecutor(this).shutdownNow();
        }

        @Override
        protected boolean local(){
            Context context = Context.onThread() ? Context.current() : Deferred.workerContext();
            return context != null && context.localSimulation;
        }

        @Override
        protected void requireHost(){
            if(local()) throw new IllegalStateException("The local world does not own the host network session.");
        }
    }

    private static void describe(){
        if(Context.current() != null) return;
        ByteBuffer buffer = NetworkIO.writeServerData();
        serverData = Arrays.copyOf(buffer.array(), buffer.position());
        described = Time.millis();
    }

    private static @Nullable ArcNetProvider sockets(NetProvider provider){
        if(provider instanceof ArcNetProvider sockets) return sockets;
        for(Field field : provider.getClass().getDeclaredFields()){
            if(!ArcNetProvider.class.isAssignableFrom(field.getType()) || Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if(Reflect.get(provider, field) instanceof ArcNetProvider sockets) return sockets;
        }
        return null;
    }

    private static @Nullable Context world(){
        return Context.onThread() ? Context.current() : Deferred.workerContext();
    }

    static final class EntityIds extends WorldFrame.Contents{
        private IntSet value;

        @Override
        void capture(){
            if(Vars.netClient != null) value = Engine.netClientRemoved(Vars.netClient);
        }

        @Override
        void install(){
            if(Vars.netClient == null) return;
            if(value == null) value = new IntSet();
            Engine.netClientSetRemoved(Vars.netClient, value);
        }

        @Override
        void release(){
            value = null;
        }
    }

    @Override
    public void sendAllServer(Object object, Iterable<NetConnection> connections, boolean reliable){
        delegate.sendAllServer(object, connections, reliable);
    }

    @Override
    public void sendAllServer(Object object, boolean reliable){
        Context world = world();
        if(world == null){
            delegate.sendAllServer(object, reliable);
        }else{
            world.broadcast.send(object, reliable, null);
        }
    }

    @Override
    public void sendExceptServer(NetConnection except, Object object, boolean reliable){
        Context world = world();
        if(world == null){
            delegate.sendExceptServer(except, object, reliable);
        }else{
            world.broadcast.send(object, reliable, except);
        }
    }

    @Override public void connectClient(String ip, int port, Runnable success) throws IOException{ delegate.connectClient(ip, port, success); }
    @Override public void sendClient(Object object, boolean reliable){ delegate.sendClient(object, reliable); }
    @Override public void disconnectClient(){ delegate.disconnectClient(); }
    @Override public void discoverServers(Cons<Host> callback, Runnable done){ delegate.discoverServers(callback, done); }
    @Override public void pingHost(String address, int port, Cons<Host> valid, Cons<Exception> failed){ delegate.pingHost(address, port, valid, failed); }
    @Override
    public void hostServer(int port) throws IOException{
        describe();
        delegate.hostServer(port);
    }
    @Override public Iterable<? extends NetConnection> getConnections(){ return delegate.getConnections(); }
    @Override public void closeServer(){ delegate.closeServer(); }
    @Override public void dispose(){ delegate.dispose(); }
    @Override public void setConnectFilter(ServerConnectFilter filter){ delegate.setConnectFilter(filter); }
    @Override public ServerConnectFilter getConnectFilter(){ return delegate.getConnectFilter(); }
}

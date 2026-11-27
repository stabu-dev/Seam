package seam.core;

import arc.*;
import arc.assets.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.ai.Pathfinder.*;
import mindustry.async.*;
import mindustry.audio.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.logic.*;
import mindustry.net.*;
import mindustry.type.*;
import mindustry.world.blocks.campaign.*;
import seam.annotations.Annotations.*;

@EngineSpec(classes = {
    @ClassAccess(type = Events.class, prefix = "events", fields = {"events"}),
    @ClassAccess(type = SaveVersion.class, prefix = "save", fields = {"customChunks"}),
    @ClassAccess(type = SaveFileReader.class, prefix = "saveReader", fields = {"chunkReads", "chunkNested"}),
    @ClassAccess(type = ContentLoader.class, prefix = "content", fields = {"temporaryMapper"}),
    @ClassAccess(type = AsyncCore.class, prefix = "asyncCore", fields = {"futures"}, writable = {"executor"}),
    @ClassAccess(type = PhysicsProcess.class, prefix = "physics", fields = {"futures", "refs", "pendingAdds", "physics"}),
    @ClassAccess(type = Pathfinder.class, prefix = "pathfinder", fields = {"maxUpdate", "queue", "threadList"}, writable = {"thread"}, methods = {"updateTargets", "updateFrontier"}),
    @ClassAccess(type = Flowfield.class, prefix = "flowfield", fields = {"frontier"}, writable = {"dirty"}),
    @ClassAccess(type = ControlPathfinder.class, prefix = "controlPath",
        fields = {"invalidateCheckInterval", "queue", "invalidated", "clustersToUpdate", "clustersToInnerUpdate", "invalidRequests", "threadPathRequests", "requestsToRecalculate", "fields", "fieldList"},
        writable = {"thread"},
        methods = {"checkEvents", "stop", "updateClustersComplete", "updateClustersInner", "recalculatePath", "updateFields"}),
    @ClassAccess(typeName = "mindustry.ai.ControlPathfinder$PathRequest", prefix = "pathRequest", fields = {"invalidated", "destination", "costId", "team"}, writable = {"oldCache"}),
    @ClassAccess(typeName = "mindustry.ai.ControlPathfinder$FieldCache", prefix = "fieldCache", fields = {"frontier", "mapKey"}),
    @ClassAccess(type = FogControl.class, prefix = "fog", fields = {"staticEvents", "dynamicEvents"}, writable = {"staticFogThread", "dynamicFogThread"}, methods = {"updateStatic", "updateDynamic"}),
    @ClassAccess(type = RtsAI.class, prefix = "rtsAi", fields = {"damagedSet"}),
    @ClassAccess(type = LandingPad.class, prefix = "landingPad", fields = {"waiting"}, setters = {"lastUpdateId"}),
    @ClassAccess(type = LExecutor.class, prefix = "logicExec", fields = {"unitTimeouts"}),
    @ClassAccess(type = Timer.class, prefix = "timer", fields = {"thread", "threadLock"}, writable = {"tasks"}),
    @ClassAccess(type = Timer.Task.class, prefix = "timerTask", fields = {"timer"}, writable = {"app"}),
    @ClassAccess(typeName = "arc.util.Timer$TimerThread", prefix = "timerThread", writable = {"instances", "instance"}),
    @ClassAccess(type = TaskQueue.class, prefix = "taskQueue", writable = {"runnables"}),
    @ClassAccess(type = Net.class, prefix = "net", fields = {"pingExecutor"}, writable = {"provider"}),
    @ClassAccess(type = ArcNetProvider.class, prefix = "arcNet", fields = {"server"}),
    @ClassAccess(type = Administration.class, prefix = "admins", writable = {"modified"}),
    @ClassAccess(type = NetClient.class, prefix = "netClient", writable = {"removed"}),
    @ClassAccess(type = Renderer.class, prefix = "renderer", setters = {"blocks", "fog", "minimap", "overlays", "lights"}, writable = {"launchAnimator", "clearColor", "launching", "camShakeOffset"}),
    @ClassAccess(type = BlockRenderer.class, prefix = "blockRenderer", fields = {"dark", "caches"}),
    @ClassAccess(type = FogRenderer.class, prefix = "fogRenderer", fields = {"staticFog", "dynamicFog"}),
    @ClassAccess(type = LightRenderer.class, prefix = "lightRenderer", fields = {"buffer"}),
    @ClassAccess(type = FloorRenderer.class, prefix = "floorRenderer", fields = {"shader", "cache"}),
    @ClassAccess(type = SoundControl.class, prefix = "soundControl", fields = {"filter"}, writable = {"ambientThread", "sounds"}),
    @ClassAccess(typeName = "mindustry.audio.SoundControl$SoundData", prefix = "soundData", fields = {"volume", "curVolume"}, writable = {"soundID"}),
    @ClassAccess(typeName = "mindustry.audio.SoundControl$AudioThread", prefix = "audioThread", fields = {"inputSources", "sources"}, writable = {"running"}, methods = {"doLoop"}, constructor = true),
    @ClassAccess(type = Planet.class, prefix = "planet", setters = {"campaignStats"}),
    @ClassAccess(type = UnlockableContent.class, prefix = "unlockable", writable = {"unlocked"}),
    @ClassAccess(type = AssetManager.class, prefix = "assets", fields = {"loadQueue"}),
    @ClassAccess(type = Saves.class, prefix = "saves", writable = {"current", "saving", "time", "totalPlaytime", "lastTimestamp"}),
    @ClassAccess(type = Universe.class, prefix = "universe", writable = {"seconds", "netSeconds", "secondCounter", "turn", "turnCounter", "lastLoadout", "lastLaunchResources"}, methods = {"save"})
})
final class EngineRefs{}

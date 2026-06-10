package seam;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.struct.IntSet.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.graphics.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

import static mindustry.Vars.*;

public final class SeamFloorRenderCache{
    public static boolean growSprites = true;

    private static final VertexAttribute[] attributes = {
    VertexAttribute.packedPosition,
    VertexAttribute.color,
    VertexAttribute.packedTexCoords
    };

    private static final int chunksize = 30;
    private static final int chunkunits = chunksize * tilesize;
    private static final int vertexSize = 3;
    private static final int spriteSize = vertexSize * 4;
    private static final int maxSprites = chunksize * chunksize * 9;

    private static final float packPad = tilesize * 8f;
    private static final float pad = tilesize / 2f;

    private final SeamRuntime runtime;

    private final float[] vertices = new float[maxSprites * vertexSize * 4];
    private final FloorRenderBatch batch = new FloorRenderBatch();

    private final Mat combinedMat = new Mat();
    private final Mat seamTransform = new Mat();
    private final Rect runtimeBounds = new Rect();

    private final IntSet drawnLayerSet = new IntSet();
    private final IntSeq drawnLayers = new IntSeq();
    private final ObjectSet<CacheLayer> used = new ObjectSet<>();

    private int vidx;

    private Shader shader;
    private Texture texture;
    private TextureRegion error;
    private SharedIndexBuffer indexData;

    private ChunkMesh[][][] cache;
    private boolean[][] dirty;

    private float packWidth;
    private float packHeight;

    private boolean fullReloadPending = true;
    private boolean built;
    private long version;

    private int chunksVisited;
    private int chunksCached;
    private int layersDrawn;
    private int meshesDrawn;

    public SeamFloorRenderCache(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        this.runtime = runtime;

        initGraphics();
    }

    public SeamRuntime runtime(){
        return runtime;
    }

    public boolean built(){
        return built;
    }

    public long version(){
        return version;
    }

    public void applyInvalidations(Seq<SeamRenderInvalidation> invalidations){
        if(invalidations == null || invalidations.isEmpty()){
            return;
        }

        for(SeamRenderInvalidation invalidation : invalidations){
            if(invalidation.full()){
                fullReloadPending = true;
                return;
            }
        }

        if(dirty == null){
            fullReloadPending = true;
            return;
        }

        for(SeamRenderInvalidation invalidation : invalidations){
            if(invalidation.x < 0 || invalidation.y < 0){
                continue;
            }

            markDirtyAround(invalidation.x, invalidation.y, invalidation.radius);
        }
    }

    public void clear(){
        disposeMeshes();

        cache = null;
        dirty = null;

        built = false;
        fullReloadPending = true;
        version++;
    }

    public void dispose(){
        clear();

        if(shader != null){
            shader.dispose();
            shader = null;
        }

        if(indexData != null){
            indexData.disposeUnsafe();
            indexData = null;
        }
    }

    public SeamFloorDrawResult draw(SeamView view, Rect hostBounds){
        if(view == null){
            throw new NullPointerException("view");
        }

        if(hostBounds == null){
            throw new NullPointerException("hostBounds");
        }

        if(view.runtimeId() != runtime.id){
            return SeamFloorDrawResult.failure(runtime.id, view.id(), "view runtime does not match floor cache runtime");
        }

        if(!view.renderable()){
            return SeamFloorDrawResult.failure(runtime.id, view.id(), "view is not renderable");
        }

        if(!runtime.worldReady()){
            return SeamFloorDrawResult.failure(runtime.id, view.id(), "runtime world is not ready");
        }

        SeamRuntimeValidator.validateActiveContext(runtime);

        chunksVisited = 0;
        chunksCached = 0;
        layersDrawn = 0;
        meshesDrawn = 0;

        if(!built || fullReloadPending){
            reload(false);
        }

        computeRuntimeBounds(view.projection(), hostBounds, runtimeBounds);

        drawnLayers.clear();
        drawnLayerSet.clear();

        int minx = Math.max((int)((runtimeBounds.x - pad) / chunkunits), 0);
        int miny = Math.max((int)((runtimeBounds.y - pad) / chunkunits), 0);
        int maxx = Math.min(Mathf.ceil((runtimeBounds.x + runtimeBounds.width + pad) / chunkunits), cache.length);
        int maxy = Math.min(Mathf.ceil((runtimeBounds.y + runtimeBounds.height + pad) / chunkunits), cache[0].length);

        int layers = CacheLayer.all.length;

        for(int x = minx; x <= maxx; x++){
            for(int y = miny; y <= maxy; y++){
                if(!Structs.inBounds(x, y, cache)){
                    continue;
                }

                chunksVisited++;

                if(cache[x][y].length == 0 || dirty[x][y]){
                    dirty[x][y] = false;
                    cacheChunk(x, y, false);
                }

                ChunkMesh[] chunk = cache[x][y];

                for(int i = 0; i < layers; i++){
                    if(i < chunk.length && chunk[i] != null && i != CacheLayer.walls.id && chunk[i].bounds.overlaps(runtimeBounds)){
                        drawnLayerSet.add(i);
                    }
                }
            }
        }

        IntSetIterator iterator = drawnLayerSet.iterator();

        while(iterator.hasNext){
            drawnLayers.add(iterator.next());
        }

        drawnLayers.sort();

        try{
            for(int i = 0; i < drawnLayers.size; i++){
                drawLayer(CacheLayer.all[drawnLayers.get(i)], runtimeBounds, false, view.projection());
            }
        }finally{
            Draw.flush();
            Draw.shader();
            Blending.normal.apply();
        }

        return SeamFloorDrawResult.success(
        runtime.id,
        view.id(),
        chunksVisited,
        chunksCached,
        layersDrawn,
        meshesDrawn,
        version
        );
    }

    private void reload(boolean ignoreWalls){
        disposeMeshes();

        int chunksx = Mathf.ceil((float)runtime.world.width() / chunksize);
        int chunksy = Mathf.ceil((float)runtime.world.height() / chunksize);

        cache = new ChunkMesh[chunksx][chunksy][CacheLayer.all.length];
        dirty = new boolean[chunksx][chunksy];

        texture = Core.atlas.find("grass1").texture;
        error = Core.atlas.find("env-error");

        packWidth = runtime.world.unitWidth() + packPad * 2f;
        packHeight = runtime.world.unitHeight() + packPad * 2f;

        for(int x = 0; x < chunksx; x++){
            for(int y = 0; y < chunksy; y++){
                cacheChunk(x, y, ignoreWalls);
            }
        }

        built = true;
        fullReloadPending = false;
        version++;
    }

    private void disposeMeshes(){
        if(cache == null){
            return;
        }

        for(ChunkMesh[][] x : cache){
            for(ChunkMesh[] y : x){
                for(ChunkMesh mesh : y){
                    if(mesh != null){
                        mesh.dispose();
                    }
                }
            }
        }
    }

    private void markDirtyAround(int centerX, int centerY, int radius){
        if(dirty == null){
            fullReloadPending = true;
            return;
        }

        int minX = Math.max(0, centerX - radius);
        int minY = Math.max(0, centerY - radius);
        int maxX = Math.min(runtime.world.width() - 1, centerX + radius);
        int maxY = Math.min(runtime.world.height() - 1, centerY + radius);

        int minChunkX = minX / chunksize;
        int minChunkY = minY / chunksize;
        int maxChunkX = maxX / chunksize;
        int maxChunkY = maxY / chunksize;

        for(int x = minChunkX; x <= maxChunkX; x++){
            for(int y = minChunkY; y <= maxChunkY; y++){
                if(x >= 0 && y >= 0 && x < dirty.length && y < dirty[0].length){
                    dirty[x][y] = true;
                }
            }
        }
    }

    private void beginDraw(SeamProjection projection){
        Draw.flush();

        shader.bind();

        projection.writeTransform(seamTransform);

        shader.setUniformMatrix4(
        "u_projectionViewMatrix",
        combinedMat
        .set(Core.camera.mat)
        .mul(seamTransform)
        .translate(-packPad, -packPad)
        .scale(packWidth, packHeight)
        );

        texture.bind(0);
        Gl.enable(Gl.blend);
    }

    private void drawLayer(CacheLayer layer, Rect bounds, boolean checkChanges, SeamProjection projection){
        layer.begin();

        try{
            beginDraw(projection);

            int minx = Math.max((int)((bounds.x - pad) / chunkunits), 0);
            int miny = Math.max((int)((bounds.y - pad) / chunkunits), 0);
            int maxx = Math.min(Mathf.ceil((bounds.x + bounds.width + pad) / chunkunits), cache.length);
            int maxy = Math.min(Mathf.ceil((bounds.y + bounds.height + pad) / chunkunits), cache[0].length);

            layersDrawn++;

            for(int x = minx; x <= maxx; x++){
                for(int y = miny; y <= maxy; y++){
                    if(!Structs.inBounds(x, y, cache) || cache[x][y].length == 0){
                        continue;
                    }

                    if(dirty[x][y] && checkChanges){
                        dirty[x][y] = false;
                        cacheChunk(x, y, false);
                    }

                    ChunkMesh mesh = cache[x][y][layer.id];

                    if(mesh != null && mesh.bounds.overlaps(bounds)){
                        mesh.render(shader, Gl.triangles, 0, mesh.getMaxVertices() * 6 / 4);
                        meshesDrawn++;
                    }
                }
            }
        }finally{
            layer.end();
        }
    }

    private void cacheChunk(int cx, int cy, boolean ignoreWalls){
        used.clear();

        for(int tilex = Math.max(cx * chunksize - 1, 0); tilex < (cx + 1) * chunksize + 1 && tilex < runtime.world.width(); tilex++){
            for(int tiley = Math.max(cy * chunksize - 1, 0); tiley < (cy + 1) * chunksize + 1 && tiley < runtime.world.height(); tiley++){
                Tile tile = runtime.world.rawTile(tilex, tiley);

                boolean wall = !ignoreWalls && tile.block().cacheLayer != CacheLayer.normal;

                if(wall){
                    used.add(tile.block().cacheLayer);
                }

                if(!wall || runtime.world.isAccessible(tilex, tiley)){
                    used.add(tile.floor().cacheLayer);
                }
            }
        }

        if(cache[cx][cy].length == 0){
            cache[cx][cy] = new ChunkMesh[CacheLayer.all.length];
        }

        ChunkMesh[] meshes = cache[cx][cy];

        for(CacheLayer layer : CacheLayer.all){
            if(meshes[layer.id] != null){
                meshes[layer.id].dispose();
            }

            meshes[layer.id] = null;
        }

        for(CacheLayer layer : used){
            meshes[layer.id] = cacheChunkLayer(cx, cy, layer, ignoreWalls);
        }

        chunksCached++;
        version++;
    }

    private ChunkMesh cacheChunkLayer(int cx, int cy, CacheLayer layer, boolean ignoreWalls){
        vidx = 0;

        Batch current = Core.batch;

        try{
            if(layer == CacheLayer.walls){
                growSprites = true;
            }

            Core.batch = batch;

            for(int tilex = cx * chunksize; tilex < (cx + 1) * chunksize; tilex++){
                for(int tiley = cy * chunksize; tiley < (cy + 1) * chunksize; tiley++){
                    Tile tile = runtime.world.tile(tilex, tiley);

                    if(tile == null){
                        continue;
                    }

                    Floor floor = tile.floor();

                    if(tile.block().cacheLayer == layer && layer == CacheLayer.walls && !(tile.isDarkened() && tile.data >= 5)){
                        tile.block().drawBase(tile);
                    }else if(floor.cacheLayer == layer && (ignoreWalls || runtime.world.isAccessible(tile.x, tile.y) || tile.block().cacheLayer != CacheLayer.walls || !tile.block().fillsTile)){
                        floor.drawBase(tile);
                    }else if(floor.cacheLayer != layer && layer != CacheLayer.walls){
                        floor.drawNonLayer(tile, layer);
                    }
                }
            }
        }finally{
            Core.batch = current;
            growSprites = false;
        }

        if(vidx <= 0){
            return null;
        }

        ChunkMesh mesh = new ChunkMesh(
        true,
        vidx / vertexSize,
        0,
        attributes,
        cx * tilesize * chunksize - tilesize / 2f,
        cy * tilesize * chunksize - tilesize / 2f,
        (cx + 1) * tilesize * chunksize + tilesize / 2f,
        (cy + 1) * tilesize * chunksize + tilesize / 2f
        );

        mesh.setVertices(vertices, 0, vidx);

        mesh.indices.dispose();
        mesh.indices = indexData;

        return mesh;
    }

    private void computeRuntimeBounds(SeamProjection projection, Rect hostBounds, Rect out){
        Vec2 a = projection.hostToRuntimeWorld(hostBounds.x, hostBounds.y, Tmp.v1);
        Vec2 b = projection.hostToRuntimeWorld(hostBounds.x + hostBounds.width, hostBounds.y, Tmp.v2);
        Vec2 c = projection.hostToRuntimeWorld(hostBounds.x + hostBounds.width, hostBounds.y + hostBounds.height, Tmp.v3);
        Vec2 d = projection.hostToRuntimeWorld(hostBounds.x, hostBounds.y + hostBounds.height, Tmp.v4);

        float minX = Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x));
        float minY = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
        float maxX = Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x));
        float maxY = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));

        out.set(minX, minY, maxX - minX, maxY - minY);
        out.grow(tilesize * 3f);

        float worldW = runtime.world.unitWidth();
        float worldH = runtime.world.unitHeight();

        float x1 = Math.max(0f, out.x);
        float y1 = Math.max(0f, out.y);
        float x2 = Math.min(worldW, out.x + out.width);
        float y2 = Math.min(worldH, out.y + out.height);

        if(x2 < x1 || y2 < y1){
            out.set(0f, 0f, 0f, 0f);
        }else{
            out.set(x1, y1, x2 - x1, y2 - y1);
        }
    }

    private void initGraphics(){
        short j = 0;
        short[] indices = new short[maxSprites * 6];

        for(int i = 0; i < indices.length; i += 6, j += 4){
            indices[i] = j;
            indices[i + 1] = (short)(j + 1);
            indices[i + 2] = (short)(j + 2);
            indices[i + 3] = (short)(j + 2);
            indices[i + 4] = (short)(j + 3);
            indices[i + 5] = j;
        }

        indexData = new SharedIndexBuffer(true, indices.length);
        indexData.set(indices, 0, indices.length);

        shader = new Shader(
        """
        attribute vec4 a_position;
        attribute vec4 a_color;
        attribute vec2 a_texCoord0;

        uniform mat4 u_projectionViewMatrix;

        varying vec4 v_color;
        varying vec2 v_texCoords;

        void main(){
            v_color = a_color;
            v_color.a = v_color.a * (255.0/254.0);
            v_texCoords = a_texCoord0;
            gl_Position = u_projectionViewMatrix * a_position;
        }
        """,
        """
        varying vec4 v_color;
        varying vec2 v_texCoords;

        uniform sampler2D u_texture;

        void main(){
            gl_FragColor = v_color * texture2D(u_texture, v_texCoords);
        }
        """
        );
    }

    private final class FloorRenderBatch extends Batch{
        @Override
        protected void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float rotation){
            if(region.texture != texture && region != error){
                draw(error, x, y, originX, originY, width, height, rotation);
                return;
            }

            float[] verts = vertices;
            int idx = vidx;

            vidx += spriteSize;

            final float grow = SeamFloorRenderCache.growSprites ? 0.04f : 0f;

            x -= grow;
            y -= grow;
            originX += grow;
            originY += grow;
            width += grow * 2f;
            height += grow * 2f;

            if(!Mathf.zero(rotation)){
                float worldOriginX = x + originX;
                float worldOriginY = y + originY;

                float fx = -originX;
                float fy = -originY;
                float fx2 = width - originX;
                float fy2 = height - originY;

                float cos = Mathf.cosDeg(rotation);
                float sin = Mathf.sinDeg(rotation);

                float x1 = cos * fx - sin * fy + worldOriginX;
                float y1 = sin * fx + cos * fy + worldOriginY;

                float x2 = cos * fx - sin * fy2 + worldOriginX;
                float y2 = sin * fx + cos * fy2 + worldOriginY;

                float x3 = cos * fx2 - sin * fy2 + worldOriginX;
                float y3 = sin * fx2 + cos * fy2 + worldOriginY;

                float x4 = x1 + (x3 - x2);
                float y4 = y3 - (y2 - y1);

                float u = region.u;
                float v = region.v2;
                float u2 = region.u2;
                float v2 = region.v;
                float color = this.colorPacked;

                verts[idx] = pack(x1, y1);
                verts[idx + 1] = color;
                verts[idx + 2] = Pack.packUv(u, v);

                verts[idx + 3] = pack(x2, y2);
                verts[idx + 4] = color;
                verts[idx + 5] = Pack.packUv(u, v2);

                verts[idx + 6] = pack(x3, y3);
                verts[idx + 7] = color;
                verts[idx + 8] = Pack.packUv(u2, v2);

                verts[idx + 9] = pack(x4, y4);
                verts[idx + 10] = color;
                verts[idx + 11] = Pack.packUv(u2, v);
            }else{
                float fx2 = x + width;
                float fy2 = y + height;

                float u = region.u;
                float v = region.v2;
                float u2 = region.u2;
                float v2 = region.v;
                float color = this.colorPacked;

                verts[idx] = pack(x, y);
                verts[idx + 1] = color;
                verts[idx + 2] = Pack.packUv(u, v);

                verts[idx + 3] = pack(x, fy2);
                verts[idx + 4] = color;
                verts[idx + 5] = Pack.packUv(u, v2);

                verts[idx + 6] = pack(fx2, fy2);
                verts[idx + 7] = color;
                verts[idx + 8] = Pack.packUv(u2, v2);

                verts[idx + 9] = pack(fx2, y);
                verts[idx + 10] = color;
                verts[idx + 11] = Pack.packUv(u2, v);
            }
        }

        float pack(float x, float y){
            return Pack.packUv((x + packPad) / packWidth, (y + packPad) / packHeight);
        }

        @Override
        public void flush(){
        }

        @Override
        public void setShader(Shader shader, boolean apply){
            throw new IllegalArgumentException("cache shader unsupported");
        }

        @Override
        protected void draw(Texture texture, float[] spriteVertices, int offset, int count){
            if(spriteVertices.length != 20){
                throw new IllegalArgumentException("cached vertices must be in non-mixcolor format");
            }

            float[] verts = vertices;
            float[] src = spriteVertices;

            int idx = vidx;
            int sidx = offset;

            for(int i = 0; i < 4; i++){
                verts[idx++] = pack(src[sidx++], src[sidx++]);
                verts[idx++] = src[sidx++];
                verts[idx++] = Pack.packUv(src[sidx++], src[sidx++]);
            }

            vidx += spriteSize;
        }
    }

    private static final class SharedIndexBuffer extends IndexBufferObject{
        private boolean unsafeDisposed;

        SharedIndexBuffer(boolean isStatic, int maxIndices){
            super(isStatic, maxIndices);
        }

        @Override
        public void dispose(){
        }

        public void disposeUnsafe(){
            if(unsafeDisposed){
                return;
            }

            unsafeDisposed = true;
            super.dispose();
        }
    }

    static final class ChunkMesh extends Mesh{
        final Rect bounds = new Rect();

        ChunkMesh(
        boolean isStatic,
        int maxVertices,
        int maxIndices,
        VertexAttribute[] attributes,
        float minX,
        float minY,
        float maxX,
        float maxY
        ){
            super(isStatic, maxVertices, maxIndices, attributes);
            bounds.set(minX, minY, maxX - minX, maxY - minY);
        }
    }

    @Override
    public String toString(){
        return "SeamFloorRenderCache{" +
        "runtimeId=" + runtime.id +
        ", built=" + built +
        ", version=" + version +
        ", fullReloadPending=" + fullReloadPending +
        '}';
    }
}
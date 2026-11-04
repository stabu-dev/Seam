package seam;

import org.junit.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.zip.*;

import static org.junit.Assert.*;

public class ModLoadTest{
    @Test
    public void loadsInTheUnmodifiedHeadlessServer() throws Exception{
        String output = runServer(Path.of(System.getProperty("seam.jar")), "mods\nversion\nexit\n");
        assertTrue(output, output.contains("1 mods loaded."));
        assertTrue(output, output.contains("Seam 0.1.0"));
        assertTrue(output, output.contains("official / build " + System.getProperty("seam.gameVersion")));
    }

    @Test
    public void scopesNativeDataInsideTheOfficialServer() throws Exception{
        String output = runServer(Path.of(System.getProperty("seam.testJar")), "version\n");
        assertTrue(output, output.contains("seam-context-probe: passed"));
    }

    @Test
    public void nativeProbeUsesProductionClassesWithoutShippingTheProbe() throws Exception{
        try(ZipFile library = new ZipFile(System.getProperty("seam.jar"));
            ZipFile probe = new ZipFile(System.getProperty("seam.testJar"))){
            assertNull(library.getEntry("seam/NativeProbe.class"));
            var entries = library.entries();
            while(entries.hasMoreElements()){
                var entry = entries.nextElement();
                if(!entry.getName().endsWith(".class")) continue;
                var tested = probe.getEntry(entry.getName());
                assertNotNull(entry.getName(), tested);
                try(InputStream expected = library.getInputStream(entry);
                    InputStream actual = probe.getInputStream(tested)){
                    assertArrayEquals(entry.getName(), expected.readAllBytes(), actual.readAllBytes());
                }
            }
        }
    }

    private String runServer(Path mod, String commands) throws Exception{
        Path server = Path.of(System.getProperty("seam.server"));
        assertTrue("Download the pinned official server JAR to " + server, Files.isRegularFile(server));
        Path work = Path.of(".work/foundation");
        Files.createDirectories(work);
        Path profile = Files.createTempDirectory(work, "server-").toAbsolutePath();
        Path mods = Files.createDirectories(profile.resolve("config/mods"));
        Files.copy(mod, mods.resolve("Seam.jar"));
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Path log = profile.resolve("server.log");
        Process process = new ProcessBuilder(java.toString(), "-jar", server.toString())
            .directory(profile.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try(OutputStream input = process.getOutputStream()){
            input.write(commands.getBytes(StandardCharsets.UTF_8));
            input.flush();
            assertTrue("Server timed out; see " + log, process.waitFor(45, TimeUnit.SECONDS));
            String output = Files.readString(log);
            assertEquals(output, 0, process.exitValue());
            return output;
        }finally{
            if(process.isAlive()){
                process.destroyForcibly();
                assertTrue("Server process did not stop.", process.waitFor(10, TimeUnit.SECONDS));
            }
        }
    }
}

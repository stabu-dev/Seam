package seam;

import org.junit.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class ModLoadTest{
    @Test
    public void loadsInTheUnmodifiedHeadlessServer() throws Exception{
        Path server = Path.of(System.getProperty("seam.server"));
        assertTrue("Download the pinned official server JAR to " + server, Files.isRegularFile(server));
        Path work = Path.of(".work/foundation");
        Files.createDirectories(work);
        Path profile = Files.createTempDirectory(work, "server-").toAbsolutePath();
        Path mods = Files.createDirectories(profile.resolve("config/mods"));
        Files.copy(Path.of(System.getProperty("seam.jar")), mods.resolve("Seam.jar"));
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Path log = profile.resolve("server.log");
        Process process = new ProcessBuilder(java.toString(), "-jar", server.toString())
            .directory(profile.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{
            try(OutputStream input = process.getOutputStream()){
                input.write("mods\nversion\nexit\n".getBytes(StandardCharsets.UTF_8));
            }
            assertTrue("Server timed out; see " + log, process.waitFor(45, TimeUnit.SECONDS));
            String output = Files.readString(log);
            assertEquals(output, 0, process.exitValue());
            assertTrue(output, output.contains("1 mods loaded."));
            assertTrue(output, output.contains("Seam 0.1.0"));
            assertTrue(output, output.contains("official / build " + System.getProperty("seam.gameVersion")));
        }finally{
            if(process.isAlive()){
                process.destroyForcibly();
                assertTrue("Server process did not stop.", process.waitFor(10, TimeUnit.SECONDS));
            }
        }
    }
}

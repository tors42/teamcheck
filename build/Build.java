package build;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Build {

    private static final String chariot_version = "0.0.21";

    private static final String chariotUrl = "https://repo1.maven.org/maven2/io/github/tors42/chariot/%s/chariot-%s.jar".formatted(chariot_version, chariot_version);

    public static void main(String... args) throws Exception {

        var props = Arrays.stream(args).filter(s -> s.contains("=")).map(s -> s.split("=")).collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
        var cross = Arrays.stream(args).anyMatch("cross"::equals);
        var module = props.getOrDefault("module", "teamcheck");
        var version = props.getOrDefault("version", "0.0.1-SNAPSHOT");

        var javac = ToolProvider.findFirst("javac").orElseThrow(() -> new RuntimeException("Missing javac tool"));
        var jar = ToolProvider.findFirst("jar").orElseThrow(() -> new RuntimeException("Missing jar tool"));
        var jlink = ToolProvider.findFirst("jlink").orElseThrow(() -> new RuntimeException("Missing jlink tool"));

        String prefix = module + "-" + version;

        Path out = Path.of("out");
        Path lib = Path.of("lib");

        del(out);
        del(lib);

        Path cache = Path.of("cache");
        Path moduleSrc = Path.of("src", "main");
        Path classes = out.resolve("classes");
        Path moduleOut = out.resolve("modules");
        Path metaInf = out.resolve("META-INF");
        Path manifest = out.resolve("MANIFEST.MF");

        Files.createDirectories(cache);
        Files.createDirectories(lib);
        Files.createDirectories(moduleOut);
        Files.createDirectories(metaInf);

        Path chariot = cache.resolve(chariotUrl.substring(chariotUrl.lastIndexOf('/')+1));
        if (! chariot.toFile().exists()) {
            System.out.println("Downloading " + chariotUrl);
            try (var source = URI.create(chariotUrl).toURL().openStream();
                 var target = Files.newOutputStream(chariot))
            {
                source.transferTo(target);
            }
        } else {
            System.out.println("Using " + chariot);
        }

        Files.copy(chariot, lib.resolve(chariot.getFileName()));
        Files.copy(Path.of("LICENSE"), metaInf.resolve("LICENSE"));

        Files.writeString(manifest, """
                Implementation-Title: %s
                Implementation-Version: %s
                Created-By: %s
                """.formatted(module, version, Runtime.version()));

        run(javac,
                "-encoding", "UTF-8",
                "--module-source-path", moduleSrc.toString(),
                "--module", module,
                "--module-path", lib.toString(),
                "-d", classes.toString()
           );

        run(jar,
                "--create",
                "--manifest", manifest.toString(),
                "--module-version", version,
                "--main-class", module + ".Main",
                "--file", moduleOut.resolve(prefix + ".jar").toString(),
                "-C", out.toString(), "META-INF",
                "-C", classes.resolve(module).toString(), "."
           );


        try (var stream = Files.walk(Path.of(System.getProperty("java.home")))) {
            Path nativeJmods = stream.filter(p -> p.getFileName().toString().equals("jmods"))
                .findFirst()
                .orElseThrow();

            run(jlink,
                    "--compress", "1",
                    "--strip-debug",
                    "--module-path", String.join(File.pathSeparator, nativeJmods.toString(), moduleOut.toString(), lib.toString()),
                    "--add-modules", "teamcheck",
                    "--launcher", module + "=" + module,
                    "--output", out.resolve("runtime").toString()
               );
        }


        if (cross) {
            var executor = Executors.newCachedThreadPool();

            record JdkJmods(String url, String jmods) {}

            //https://jdk.java.net/17
            var javaVersion = "17.0.1";
            var linux   = new JdkJmods("https://download.java.net/java/GA/jdk17.0.1/2a2082e5a09d4267845be086888add4f/12/GPL/openjdk-17.0.1_linux-x64_bin.tar.gz" , "linuxX64Jmods" + javaVersion);
            var macos   = new JdkJmods("https://download.java.net/java/GA/jdk17.0.1/2a2082e5a09d4267845be086888add4f/12/GPL/openjdk-17.0.1_macos-x64_bin.tar.gz", "macosX64Jmods" + javaVersion);
            var windows = new JdkJmods("https://download.java.net/java/GA/jdk17.0.1/2a2082e5a09d4267845be086888add4f/12/GPL/openjdk-17.0.1_windows-x64_bin.zip", "windowsX64Jmods" + javaVersion);

            List<Callable<Void>> tasks = Stream.of(linux, macos, windows)
                .map(jdk -> (Callable<Void>) () -> {

                        Path jmods = cache.resolve(jdk.jmods());
                        Path archive = cache.resolve(jdk.url().substring(jdk.url().lastIndexOf('/')+1));

                        if (! jmods.toFile().exists()) {
                            System.out.println("Downloading " + jdk.url());
                            try (var source = URI.create(jdk.url()).toURL().openStream();
                                 var target = Files.newOutputStream(archive))
                            {
                                source.transferTo(target);
                            }

                            if (jdk.jmods().contains("windows")) {
                                ProcessBuilder pb = new ProcessBuilder(
                                        "unzip",
                                        "-j",
                                        archive.toString(),
                                        "jdk-"+javaVersion+"/jmods/*",
                                        "-d",
                                        "cache/" + jdk.jmods());
                                pb.start().waitFor();
                            } else if (jdk.jmods().contains("linux")) {
                                ProcessBuilder pb = new ProcessBuilder(
                                        "tar",
                                        "xzf",
                                        archive.toString(),
                                        "-C",
                                        "cache",
                                        "jdk-"+javaVersion+"/jmods/",
                                        "--transform=s/jdk-"+javaVersion+".jmods/"+jdk.jmods()+"/g"
                                        );
                                pb.start().waitFor();
                            } else if (jdk.jmods().contains("macos")) {
                                ProcessBuilder pb = new ProcessBuilder(
                                        "tar",
                                        "xzf",
                                        archive.toString(),
                                        "-C",
                                        "cache",
                                        "./jdk-"+javaVersion+".jdk/Contents/Home/jmods/",
                                        "--transform=s/..jdk-"+javaVersion+".jdk.Contents.Home.jmods/"+jdk.jmods()+"/g");
                                pb.start().waitFor();
                            }

                        } else {
                            System.out.println("Using " + jmods);
                        }

                        return null;

                    }).toList();

            executor.invokeAll(tasks);
            executor.shutdown();


            run(jlink,
                    "--compress", "1",
                    "--module-path", String.join(File.pathSeparator, "cache/windowsX64Jmods", moduleOut.toString(), lib.toString()),
                    "--add-modules", "teamcheck",
                    "--launcher", module + "=" + module,
                    "--output", out.resolve("windows").resolve(prefix).toString()
               );

            run(jlink,
                    "--compress", "1",
                    "--module-path", String.join(File.pathSeparator, "cache/linuxX64Jmods", moduleOut.toString(), lib.toString()),
                    "--add-modules", "teamcheck",
                    "--launcher", module + "=" + module,
                    "--output", out.resolve("linux").resolve(prefix).toString()
               );

            run(jlink,
                    "--compress", "1",
                    "--module-path", String.join(File.pathSeparator, "cache/macosX64Jmods", moduleOut.toString(), lib.toString()),
                    "--add-modules", "teamcheck",
                    "--launcher", module + "=" + module,
                    "--output", out.resolve("macos").resolve(prefix).toString()
               );

            new ProcessBuilder(
                    "zip",
                    "-r",
                    "../" + prefix + "-windows.zip",
                    "."
                    ).directory(new File("out/windows")).start().waitFor();

            new ProcessBuilder(
                    "zip",
                    "-r",
                    "../" + prefix + "-linux.zip",
                    "."
                    ).directory(new File("out/linux")).start().waitFor();

            new ProcessBuilder(
                    "zip",
                    "-r",
                    "../" + prefix + "-macos.zip",
                    "."
                    ).directory(new File("out/macos")).start().waitFor();
        }
    }


    static void del(Path dir) {
        if (dir.toFile().exists()) {
            try (var files = Files.walk(dir)) {
                files.sorted(Collections.reverseOrder()).map(Path::toFile).forEach(f -> f.delete());
            } catch (Exception e) {}
        }
    }

    static void run(ToolProvider tool, String... args) {
        var out = new StringWriter();
        var err = new StringWriter();

        int exitCode = tool.run(new PrintWriter(out), new PrintWriter(err), args);

        if (exitCode != 0) {
            out.flush();
            err.flush();
            System.err.format("""
                    %s exited with code %d
                    args:   %s
                    stdout: %s
                    stderr: %s%n""",
                    tool, exitCode, Arrays.stream(args).collect(Collectors.joining(" ")),
                    out.toString(), err.toString());
            System.exit(exitCode);
        }
    }

}

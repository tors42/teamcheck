package build;

import java.io.*;
import java.lang.Runtime.Version;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.spi.ToolProvider;
import java.util.stream.*;


public class Build {

    private static final String chariot_version = "0.1.8";
    static String repo = "https://repo1.maven.org/maven2";

    static String chariot_path = "io/github/tors42/chariot/%s/chariot-%s.jar".formatted(chariot_version, chariot_version);

    static String chariotUrl= "%s/%s".formatted(repo, chariot_path);

    public static void main(String... args) throws Exception {

        var props = Arrays.stream(args).filter(s -> s.contains("=")).map(s -> s.split("=")).collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
        var cross = Arrays.stream(args).anyMatch("cross"::equals);
        var module = props.getOrDefault("module", "teamcheck");
        var version = props.getOrDefault("version", "0.0.1-SNAPSHOT");
        var timestamp = props.getOrDefault("timestamp", ZonedDateTime.now()
            .withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

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
                "--module-source-path", moduleSrc.toString(),
                "--module", module,
                "--module-path", lib.toString(),
                "-d", classes.toString()
           );

        run(jar,
                "--create",
                "--date", timestamp,
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
                    //"--add-options", " --enable-preview",
                    "--compress", "zip-9",
                    "--no-man-pages",
                    "--no-header-files",
                    "--strip-debug",
                    "--module-path", String.join(File.pathSeparator, nativeJmods.toString(), moduleOut.toString(), lib.toString()),
                    "--add-modules", module,
                    "--launcher", module + "=" + module,
                    "--output", out.resolve("runtime").toString()
               );
        }


        if (cross) {

            record Jdk(String os, String arch, String ext) {
                String osAndArch() { return String.join("-", os, arch); }
            }
            record VersionedJdk(Jdk jdk, Version version) {
                String toVersionString() { return version.version().stream().map(String::valueOf).collect(Collectors.joining(".")); }
                String toBuildString() { return version.build().map(String::valueOf).orElseThrow(); }
            }
            record DownloadableVersionedJdk(VersionedJdk versionedJdk, URI uri) {}
            record JmodsPath(DownloadableVersionedJdk downloadableVersionedJdk, Path jmods) {}

            //https://download.java.net/java/GA/jdk23.0.1/c28985cbf10d4e648e4004050f8781aa/11/GPL/openjdk-23.0.1_linux-x64_bin.tar.gz
            Version javaVersion = Version.parse("23.0.1+11");

            var jdks = List.of(
                    new Jdk("linux", "x64", "tar.gz"),
                    new Jdk("linux", "aarch64", "tar.gz"),
                    new Jdk("macos", "x64", "tar.gz"),
                    new Jdk("macos", "aarch64", "tar.gz"),
                    new Jdk("windows", "x64", "zip")
                    );
            Function<VersionedJdk, URI> toOpenJdkUri = vjdk -> {
                String javaVersionString = vjdk.toVersionString();
                String buildString       = vjdk.toBuildString();

                String id = "c28985cbf10d4e648e4004050f8781aa";
                String baseUrl = "https://download.java.net/java/GA/jdk%s/%s/%s/GPL/".formatted(javaVersionString, id, buildString);
                String filenameTemplate = "openjdk-%s".formatted(javaVersionString).concat("_%s-%s_bin.%s");

                var jdk = vjdk.jdk();
                URI uri = URI.create(baseUrl + filenameTemplate.formatted(jdk.os(), jdk.arch(), jdk.ext()));

                return uri;
            };

            BiFunction<DownloadableVersionedJdk, Path, Path> toJmodsPath = (jdk, cacheDir) -> cacheDir.resolve("jmods-" + jdk.versionedJdk().jdk().osAndArch() + "-" + jdk.versionedJdk().toVersionString());

            BiConsumer<Path, JmodsPath> unpack = (archive, target) -> {

                String javaVersionString = target.downloadableVersionedJdk().versionedJdk().toVersionString();
                String cacheDir = target.jmods().getParent().toString();

                ProcessBuilder pb = switch(target.downloadableVersionedJdk().versionedJdk().jdk().os()) {
                    case "windows" -> new ProcessBuilder(
                            "unzip",
                            "-j",
                            archive.toString(),
                            "jdk-"+javaVersionString+"/jmods/*",
                            "-d",
                            target.jmods().toString());

                    case "linux" -> new ProcessBuilder(
                            "tar",
                            "xzf",
                            archive.toString(),
                            "-C",
                            cacheDir,
                            "jdk-"+javaVersionString+"/jmods/",
                            "--transform=s/jdk-"+javaVersionString+".jmods/"+target.jmods().getFileName()+"/g");

                    case "macos" -> new ProcessBuilder(
                            "tar",
                            "xzf",
                            archive.toString(),
                            "-C",
                            cacheDir,
                            "./jdk-"+javaVersionString+".jdk/Contents/Home/jmods/",
                            "--transform=s/..jdk-"+javaVersionString+".jdk.Contents.Home.jmods/"+target.jmods().getFileName()+"/g");

                   default -> throw new IllegalArgumentException(target.downloadableVersionedJdk().versionedJdk().jdk().os());
                };

                try {
                    int exitValue = pb.start().waitFor();
                    if (exitValue != 0) {
                        System.out.println("Failure executing " + cacheDir + " - " + pb.toString());
                    }
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            };


            var jmodsPaths = jdks.stream()
                .map(jdk -> new VersionedJdk(jdk, javaVersion))
                .map(jdk -> new DownloadableVersionedJdk(jdk, toOpenJdkUri.apply(jdk)))
                .map(jdk -> new JmodsPath(jdk, toJmodsPath.apply(jdk, cache)))
                .toList();

            var downloadAndUnpackTasks = jmodsPaths.stream()
                .map(jdk -> (Callable<Void>) () -> {
                    Path jmods = jdk.jmods();
                    if (! jmods.toFile().exists()) {
                        Path archive = Files.createTempFile(jdk.downloadableVersionedJdk().versionedJdk().jdk().osAndArch()+"-", "");
                        archive.toFile().deleteOnExit();

                        System.out.println("Downloading " + jdk.downloadableVersionedJdk());
                        try (var source = jdk.downloadableVersionedJdk().uri().toURL().openStream();
                             var target = Files.newOutputStream(archive))
                        {
                            source.transferTo(target);
                        }

                        unpack.accept(archive, jdk);
                    } else {
                        System.out.println("Using " + jmods);
                    }
                    return null;
                }
                )
                .toList();

            var executor = Executors.newFixedThreadPool(jdks.size());
            executor.invokeAll(downloadAndUnpackTasks);
            executor.shutdown();


            jmodsPaths.stream()
                .forEach(jdk -> {
                    run(jlink,
                            "--compress", "zip-9",
                            "--no-man-pages",
                            "--no-header-files",
                            "--module-path", String.join(File.pathSeparator, jdk.jmods().toString(), moduleOut.toString(), lib.toString()),
                            "--add-modules", module,
                            "--launcher", module + "=" + module,
                            "--output", out.resolve(jdk.downloadableVersionedJdk().versionedJdk().jdk().osAndArch()).resolve(prefix).toString()
                       );
                });

            jmodsPaths.stream()
                .forEach(jdk -> {
                    var pb = new ProcessBuilder(
                            "zip",
                            "-r",
                            "../" + prefix + "-" + jdk.downloadableVersionedJdk().versionedJdk().jdk().osAndArch() + ".zip",
                            "."
                            ).directory(new File("out/" + jdk.downloadableVersionedJdk().versionedJdk().jdk().osAndArch()));

                    try {
                        pb.start().waitFor();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

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

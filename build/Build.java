String chariot_version = "0.2.0";

void main(String... args) throws Exception {
    URL chariotUrl = URI.create(
            "https://repo1.maven.org/maven2/io/github/tors42/chariot/%1$s/chariot-%1$s.jar"
            .formatted(chariot_version)).toURL();

    var props = Arrays.stream(args).filter(s -> s.contains("=")).map(s -> s.split("="))
        .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
    String module = props.getOrDefault("module", "teamcheck");
    String version = props.getOrDefault("version", "0.0.1-SNAPSHOT");
    String timestamp = props.getOrDefault("timestamp", ZonedDateTime.now()
        .withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    boolean cross = Arrays.stream(args).anyMatch("cross"::equals);
    String prefix = module + "-" + version;

    var javac = ToolProvider.findFirst("javac").orElseThrow(() -> new RuntimeException("Missing javac tool"));
    var jar = ToolProvider.findFirst("jar").orElseThrow(() -> new RuntimeException("Missing jar tool"));
    var jlink = ToolProvider.findFirst("jlink").orElseThrow(() -> new RuntimeException("Missing jlink tool"));

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

    for (var dir : List.of(cache, lib, moduleOut, metaInf)) Files.createDirectories(dir);

    Path chariot = cache.resolve(Path.of(chariotUrl.getPath()).getFileName());
    if (chariot.endsWith("SNAPSHOT.jar")) Files.delete(chariot);
    if (! chariot.toFile().exists()) {
        IO.println("Copying " + chariotUrl);
        Files.copy(chariotUrl.openStream(), chariot);
    } else {
        IO.println("Using " + chariot);
    }

    Files.copy(chariot, lib.resolve(chariot.getFileName()));
    Files.copy(Path.of("LICENSE"), metaInf.resolve("LICENSE"));

    Files.writeString(manifest, """
            Implementation-Title: %s
            Implementation-Version: %s
            Created-By: %s
            """.formatted(module, version, Runtime.version()));

    run(javac,
            "--module-source-path", moduleSrc,
            "--module", module,
            "--module-path", lib,
            "-d", classes
       );

    run(jar,
            "--create",
            "--date", timestamp,
            "--manifest", manifest,
            "--module-version", version,
            "--main-class", module + ".Main",
            "--file", moduleOut.resolve(prefix + ".jar"),
            "-C", out, "META-INF",
            "-C", classes.resolve(module), "."
       );

    Path nativeJmods = Path.of(System.getProperty("java.home"));

    run(jlink,
            //"--add-options", " --enable-preview",
            "--compress", "zip-9",
            "--no-man-pages",
            "--no-header-files",
            "--strip-debug",
            "--module-path", String.join(File.pathSeparator, nativeJmods.toString(), moduleOut.toString(), lib.toString()),
            "--add-modules", module,
            "--launcher", module + "=" + module,
            "--output", out.resolve("runtime")
       );

    if (cross) {
        buildForAllPlatforms(jlink, module, prefix, out, moduleOut, cache, lib);
    }

}

void buildForAllPlatforms(ToolProvider jlink, String module, String prefix, Path out, Path moduleOut, Path cache, Path lib) throws Exception {

    record Jdk(String os, String arch, String ext) {
        String osAndArch() { return String.join("-", os, arch); }
    }
    record VersionedJdk(Jdk jdk, Runtime.Version version) {
        String toVersionString() { return version.version().stream().map(String::valueOf).collect(Collectors.joining(".")); }
        String toBuildString() { return version.build().map(String::valueOf).orElseThrow(); }
    }
    record DownloadableVersionedJdk(VersionedJdk versionedJdk, URI uri) {}
    record JmodsPath(DownloadableVersionedJdk downloadableVersionedJdk, Path jmods) {}

    //https://download.java.net/java/early_access/jdk25/32/GPL/openjdk-25-ea+32_linux-x64_bin.tar.gz
    Runtime.Version javaVersion = Runtime.Version.parse("25-ea+32");

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
        String baseUrl = "https://download.java.net/java/early_access/jdk%s/%s/GPL/".formatted(javaVersionString, buildString);
        String filenameTemplate = "openjdk-%s%s".formatted(javaVersionString, "_%s-%s_bin.%s");
        URI uri = URI.create(baseUrl + filenameTemplate.formatted(vjdk.jdk().os(), vjdk.jdk().arch(), vjdk.jdk().ext()));
        return uri;
    };

    BiFunction<DownloadableVersionedJdk, Path, Path> toJmodsPath =
        (jdk, cacheDir) -> cacheDir.resolve("jmods-%s-%s".formatted(
                    jdk.versionedJdk().jdk().osAndArch(),
                    jdk.versionedJdk().toVersionString()));

    IO.println("Non platform-independent unpackaging of archives... Known to have worked on Linux with tools unzip and tar of some versions");
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
                IO.println("Failure executing " + cacheDir + " - " + pb.toString());
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
                IO.println("Downloading " + jdk.downloadableVersionedJdk());
                Files.copy(jdk.downloadableVersionedJdk().uri().toURL().openStream(), archive);
                unpack.accept(archive, jdk);
            } else {
                IO.println("Using " + jmods);
            }
            return null;
        }).toList();

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
                    "--output", out.resolve(jdk.downloadableVersionedJdk().versionedJdk().jdk().osAndArch()).resolve(prefix)
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

void del(Path dir) {
    if (Files.exists(dir)) {
        try (var files = Files.walk(dir).map(Path::toFile)) {
            files.toList().reversed().forEach(File::delete);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}

void run(ToolProvider tool, Object... oargs) {
    String[] args = Arrays.stream(oargs).map(Object::toString).toArray(String[]::new);
    var out = new StringWriter();
    var err = new StringWriter();

    int exitCode = tool.run(new PrintWriter(out), new PrintWriter(err), args);

    if (exitCode != 0) {
        out.flush();
        err.flush();
        IO.println("""
                %s exited with code %d
                args:   %s
                stdout: %s
                stderr: %s%n""".formatted(
                tool, exitCode, String.join(" ", args),
                out.toString(), err.toString()));
        System.exit(exitCode);
    }
}

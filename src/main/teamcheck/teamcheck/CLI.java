package teamcheck;

import chariot.Client;

class CLI {

    public static void main(String... args) {
        usage(args);

        var prefs = Main.prefs();

        var client = Client.load(prefs);
        client.store(prefs);
        try { prefs.flush();} catch (Exception e) { }

        CheckUtil.of(
                args[0],
                t -> System.out.format("Team: %s%nMembers: %s%n", t.name(), t.nbMembers()),
                u -> System.out.format("%-16s - %s%n", u.name(), u.url()),
                u -> u.tosViolation()
                )
            .process(client);
    }

    static void usage(String... args) {
        if (args.length != 1) {
            var cmd = java.util.Optional.ofNullable(System.getProperty("sun.java.command"));
            var msg = cmd.map(s -> "java -m " + s).orElse("<command>");
            System.out.format("Usage: %s <teamId>%n", msg);
            System.exit(0);
        }
    }
}

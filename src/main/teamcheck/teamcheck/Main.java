package teamcheck;

import java.awt.GraphicsEnvironment;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;

import chariot.Client;
import teamcheck.gui.TeamChooser;

class Main {

    public static void main(String... args) throws Exception {

        if (GraphicsEnvironment.isHeadless()) {
            System.err.format("Headless environment, try CLI instead...%n");
            System.exit(1);
        }

        var prefs = prefs();
        var client = Client.load(prefs);

        SwingUtilities.invokeLater(() -> new TeamChooser(client, prefs));
    }

    static Preferences prefs() {
        return Preferences.userRoot().node(System.getProperty("prefs", Main.class.getModule().getName()));
    }
}

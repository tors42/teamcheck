package teamcheck.gui;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

public class CheckeredPanel extends JPanel {

    final Color light;
    final Color dark;

    GradientPaint lightPaint = null;
    GradientPaint darkPaint = null;

    public CheckeredPanel() {
        this(Color.decode("#eeeeee"), Color.decode("#dfdfdf"));
    }

    public CheckeredPanel(Color light, Color dark) {
        this.light = light;
        this.dark = dark;
        setBorder(BorderFactory.createLineBorder(Color.BLACK));
    }

    @Override
    public void paintComponent(Graphics g1) {
        int xNum = 16;
        int length = getWidth()/xNum;
        int yNum = getHeight()/length;

        if (lightPaint == null) lightPaint = new GradientPaint(0,0, light, (int) (0.3*getWidth()), getHeight(), light.darker(), false);
        if (darkPaint == null) darkPaint = new GradientPaint(0,0, dark, (int) (0.3*getWidth()), getHeight(), dark.darker(), false);

        var g = (Graphics2D) g1;
        for (int x = -1; x < xNum; x++) {
            for (int y = 0; y < yNum+1; y++) {
                g.setPaint(x%2 == 0 ? lightPaint : darkPaint);
                g.fillRect( (x + y%2)*length, y*length, length, length);
            }
        }
    }
}

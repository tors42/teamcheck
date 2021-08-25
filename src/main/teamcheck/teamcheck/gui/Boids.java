package teamcheck.gui;

import static java.util.function.Predicate.not;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import chariot.model.User;
import teamcheck.CheckUtil.Mate;

class Boids extends JComponent {

    boolean running = false;

    static Random random = new Random();
    record Boid(Vec position, Vec velocity, boolean curious, Color color, int size, String name) {}

    List<Boid> boids =    Collections.synchronizedList(new ArrayList<>());
    List<Boid> toAdd =    Collections.synchronizedList(new ArrayList<>());
    List<Boid> toRemove = Collections.synchronizedList(new ArrayList<>());

    long prevAdd = t();

    MouseListener ml = null;

    void weaponize(Mate mate) {

        SwingUtilities.invokeLater(() -> setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)));

        ml = new MouseListener()  {

            @Override
            public void mouseClicked(MouseEvent e) {
                Predicate<Boid> clickHitBoid = b ->
                    Math.sqrt(Math.pow(b.position().vec()[0]               - e.getX(), 2) +
                              Math.pow(getHeight() - b.position().vec()[1] - e.getY(), 2)) < b.size();

                var target = boids.stream()
                    .filter(clickHitBoid)
                    .findFirst();

                target.ifPresent( b -> {
                    var kicked = mate.kick(b.name());

                    if (kicked) {
                        toRemove.add(b);
                    }
                });
            }

            @Override public void mousePressed(MouseEvent e) { }
            @Override public void mouseReleased(MouseEvent e) { }
            @Override public void mouseEntered(MouseEvent e) { }
            @Override public void mouseExited(MouseEvent e) { }
        };

        addMouseListener(ml);
    }

    void deweaponize() {
        SwingUtilities.invokeLater(() -> setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)));
        removeMouseListener(ml);
        ml = null;
    }

    void spawnBoidOnEdge(User user) {
        var width = getWidth();
        var height = getHeight();
        var positionOnEdge = switch(random.nextInt(2)) {
            case 0  -> Vec.of(random.nextBoolean() ? 0 : width,
                              random.nextInt(height));
            default -> Vec.of(random.nextInt(width),
                              random.nextBoolean() ? 0 : height);
        };
        var velocityTowardsCenter = Vec.of(width/2, height/2).sub(positionOnEdge).normalize().mul(0.5);
        var name = user.username();
        var mark = user.tosViolation();
        var color = mark ? Color.red.darker().darker() : Color.green.darker().darker();
        var size = mark ? 20 : 10;
        toAdd.add(new Boid(positionOnEdge, velocityTowardsCenter, mark, color, size, name));
    }

    void start() {
        running = true;
        while (running) {
            var t = t();
            step();
            repaint();
            handleBoidsCountChanges();
            idle(t);
         }
    }

    void stop() {
        running = false;
        if (ml != null) {
            removeMouseListener(ml);
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        boids.stream().forEach(b -> drawBoid(b, g));
    }

    void drawBoid(Boid b, Graphics g) {
        int x = (int) b.position().vec()[0] - b.size()/2;
        int y = getHeight() - (int) b.position().vec()[1] - b.size()/2;

        g.setColor(b.color());
        g.fillOval(x, y, b.size(), b.size());

        g.setColor(Color.pink.darker().darker());
        g.drawString(b.name(), x, y);
    }

    void step() {

        record BoidAction(Boid boid, Vec action) {};

        var boidActions = boids.stream()
            .filter(Objects::nonNull)
            .map(boid -> {
                var flee = flee(boid);
                if (flee.vec()[0] != 0 || flee.vec()[1] != 0) {
                    return new BoidAction(boid, flee);
                } else {
                    return new BoidAction(boid, Stream.of(
                                // Boids move in same direction
                                alignment(boid),

                                // Boids move to the centre of mass
                                cohesion(boid),

                                // Boids keep a distance from eachother
                                separation(boid),

                                // Boids don't stray far outside boundary
                                boundary(boid)
                                ).reduce(Vec.of(0,0), Vec::add));
                }
            }
        ).toList();

        boidActions.stream().forEach(boidAction -> {
            var adjusted = boidAction.boid.velocity().add(boidAction.action);

            if (adjusted.len() > 5) adjusted.normalize().mul(4);
            if (adjusted.len() < 3) adjusted.normalize().mul(3);

            boidAction.boid.position().add(boidAction.boid.velocity());
        });
    }

    Vec alignment(Boid boid) {

        var velocities = boids.stream()
            .filter(Objects::nonNull)
            .filter(not(boid::equals))
            .filter(withinDistance(150, boid.position()))
            .limit(10)
            .map(Boid::velocity)
            .toList();

        var size = velocities.size();

        return size > 0 ?
            velocities.stream()
                .reduce(Vec.of(0,0), Vec::add)
                .div(size)
                .sub(boid.velocity())
                .div(8) :
            Vec.of(0,0);
    }

    Vec cohesion(Boid boid) {

        var positions = boids.stream()
            .filter(Objects::nonNull)
            .filter(not(boid::equals))
            .filter(withinDistance(20, boid.position()))
            .limit(10)
            .map(Boid::position)
            .toList();

        var size = positions.size();

        return size > 0 ?
            positions.stream()
                .reduce(Vec.of(0,0), Vec::add)
                .div(size)
                .sub(boid.position())
                .div(100) :
            Vec.of(0,0);
    }

    Vec separation(Boid boid) {
        return boids.stream()
            .filter(Objects::nonNull)
            .filter(not(boid::equals))
            .filter(withinDistance(20, boid.position()))
            .limit(10)
            .map(Boid::position)
            .map(pos -> Vec.of(pos).sub(boid.position()))
            .reduce(Vec.of(0,0), Vec::sub);
    }

    Vec boundary(Boid boid) {
        var x = boid.position().vec()[0];
        var y = boid.position().vec()[1];
        var inset      = 50;
        var adjustment = 5;
        int height = getHeight();
        int width = getWidth();

        var v = Vec.of(0,0);

        if (x < inset) {
            v.vec()[0] = (inset - x) / adjustment;
        } else if (x > width-inset) {
            v.vec()[0] = - (x - (width-inset)) / adjustment;
        }

        if (y < inset) {
            v.vec()[1] = (inset - y) / adjustment;
        } else if(y > height-inset) {
            v.vec()[1] = -(y - (height-inset)) / adjustment;
        }

        return v;
    }

    Vec flee(Boid boid) {
        var p = getMousePosition();
        if (p != null) {
            var cursorPos = Vec.of(p.getX(), getHeight() - p.getY());
            if (withinDistance(50, cursorPos).test(boid)) {
                if (boid.curious()) {
                    return Vec.of(cursorPos).sub(boid.position());
                } else {
                    return Vec.of(boid.position()).sub(cursorPos);
                }
            }
        }
        return Vec.of(0,0);
    }

    private static Predicate<Boid> withinDistance(float distance, Vec position) {
        return b -> Vec.of(b.position()).sub(position).len() < distance;
    }

    private static void idle(long t0) {
        var idle = 30l - Math.abs(t()-t0);
        if (idle > 0) try { Thread.sleep(idle); } catch (InterruptedException e) { }
    }

    private static long t() {
        return System.currentTimeMillis();
    }

    private void handleBoidsCountChanges() {
        if (! toRemove.isEmpty()) {
            boids.removeAll(toRemove);
            toRemove.clear();
        }

        var t = t();
        if (t - prevAdd > 100) {
            var iter = toAdd.iterator();
            if (iter.hasNext()) {
                var boid = iter.next();
                boids.add(boid);
                iter.remove();
                prevAdd = t;
            }
        }
    }
}


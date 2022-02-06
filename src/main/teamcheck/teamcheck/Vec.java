package teamcheck;

public record Vec(double x, double y) {

    public Vec withX(double newX) {
        return new Vec(newX, y);
    }

    public Vec withY(double newY) {
        return new Vec(x, newY);
    }

    public static Vec of(double x, double y) {
        return new Vec(x, y);
    }

    public static Vec of(Vec other) {
        return of(other.x, other.y);
    }

    public Vec add(Vec other) {
        return of(x+other.x, y+other.y);
    }

    public Vec sub(Vec other) {
        return of(x-other.x, y-other.y);
    }

    public Vec div(double d) {
        if (d == 0) throw new IllegalArgumentException();
        return of(x/d, y/d);
    }

    public Vec mul(double m) {
        return of(x*m, y*m);
    }

    public Vec normalize() {
        var m = len();
        if (m > 0) {
            return div(m);
        } else {
            return this;
        }
    }

    public double len() {
        return Math.sqrt(x*x + y*y);
    }

}

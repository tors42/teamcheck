package teamcheck.gui;

record Vec(double[] vec) {

    static Vec of(double x, double y) {
        return new Vec(new double[] {x,y});
    }

    static Vec of(Vec other) {
        return of(other.vec[0], other.vec[1]);
    }

    Vec add(Vec other) {
        vec[0] = vec[0] + other.vec[0];
        vec[1] = vec[1] + other.vec[1];
        return this;
    }

    Vec sub(Vec other) {
        vec[0] = vec[0] - other.vec[0];
        vec[1] = vec[1] - other.vec[1];
        return this;
    }

    Vec div(double d) {
        if (d == 0) throw new IllegalArgumentException();
        vec[0] = vec[0] / d;
        vec[1] = vec[1] / d;
        return this;
    }

    Vec mul(double m) {
        vec[0] = vec[0] * m;
        vec[1] = vec[1] * m;
        return this;
    }

    Vec normalize() {
        var m = len();
        if (m > 0) {
            return div(m);
        } else {
            return this;
        }
    }

    double len() {
        return Math.sqrt(vec[0]*vec[0] + vec[1]*vec[1]);
    }

}

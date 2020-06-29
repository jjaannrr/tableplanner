package net.landj.tableplannerj.model;

import org.jetbrains.annotations.NotNull;

import java.util.stream.DoubleStream;

import static net.landj.tableplannerj.TablePlannerJ.formatDouble;

public final class ParameterStats {
    private double min;
    private double max;
    private double avg;
    private double med;
    private final ParameterStats.Parameter parameter;

    public final double getMin() {
        return this.min;
    }

    public final double getMax() {
        return this.max;
    }

    public final double getAvg() {
        return this.avg;
    }

    public final double getMed() {
        return this.med;
    }

    public final void calculate(@NotNull double[] values) {
        double[] sorted = DoubleStream.of(values).sorted().toArray();
        this.min = sorted.length > 0 ? sorted[0] : 0;
        this.max = sorted.length > 0 ? sorted[sorted.length - 1] : Double.MAX_VALUE;
        this.avg = DoubleStream.of(values).average().orElse(0);
        this.med = sorted.length > 0 ? sorted[sorted.length / 2] : 0;
    }

    @NotNull
    public String toString() {
        return this.parameter + ": min = " + formatDouble(min, 2)
                + ", max = " + formatDouble(max, 2)
                + ", avg = " + formatDouble(avg, 2)
                + ", med = " + formatDouble(med, 2);
    }

    public ParameterStats(@NotNull ParameterStats.Parameter parameter) {
        this.parameter = parameter;
    }

    public enum Parameter {
        SCORE,
        DIVERSITY,
        FOLLOW_UPS
    }
}

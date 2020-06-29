package net.landj.tableplannerj;

import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

public final class TablePlannerJ {
    public static void main(@NotNull String[] args) {
        new CommandLine(new TablePlanner())
                .addSubcommand(new CommandLine.HelpCommand())
                .execute(args);
    }

    public static String formatDouble(double d, int digits) {
        String format = String.format("%%.%df", digits);
        return String.format(format, d);
    }
}

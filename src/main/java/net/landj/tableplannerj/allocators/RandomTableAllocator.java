package net.landj.tableplannerj.allocators;

import net.landj.tableplannerj.model.Guest;
import net.landj.tableplannerj.model.Table;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Just randomly select next table<p/>
 * This won't easily find a seating plan when using follow ups and table scoring.
 */
public final class RandomTableAllocator extends NextTableAllocator {
    @NotNull
    public Table nextTable(@NotNull Guest guest, @NotNull List<Table> tables, int round) {
        return randomise(tables.stream().filter(guest::hasNotSatAt).collect(Collectors.toList()));
    }
}

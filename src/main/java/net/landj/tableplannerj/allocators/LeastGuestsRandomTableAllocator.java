package net.landj.tableplannerj.allocators;

import net.landj.tableplannerj.model.Guest;
import net.landj.tableplannerj.model.Table;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Allocate random table with least guests (with randomness).<p/>
 * When there are multiple such tables, pick a random one.
 */
public final class LeastGuestsRandomTableAllocator extends NextTableAllocator {
    @NotNull
    public Table nextTable(@NotNull Guest guest, @NotNull List<Table> tables, int round) {
        List<Pair<Integer, Table>> tablesBySuitability = tables.stream()
                .filter(guest::hasNotSatAt)
                .map(table -> new ImmutablePair<>(table.getNoOfGuestsAtTable(round), table))
                .sorted(Comparator.comparingInt(entry -> entry.left)).collect(Collectors.toList());

        return randomise(tablesBySuitability.stream()
                .filter(pair -> pair.getLeft().equals(tablesBySuitability.get(0).getLeft()))
                .map(Pair::getRight)
                .collect(Collectors.toList()));

    }
}

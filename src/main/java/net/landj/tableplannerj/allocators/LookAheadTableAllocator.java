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
 * Check future guest rating and pick a table most suitable for given guest (with randomness).<p/>
 * If multiple suitable tables are available, pick a random one.<p/>
 * This works nicely when there is a perfect fit scenario available.<br/>
 * It won't find a good one when there isn't. In those instances it would tend to find a victim (guest that would meet the least number of other guests).
 * This doesn't play nicely with the updated logic using follow ups and table score.
 */
public final class LookAheadTableAllocator extends NextTableAllocator {
    @NotNull
    public Table nextTable(@NotNull Guest guest, @NotNull List<Table> tables, int round) {
        List<Pair<Double, Table>> tablesBySuitability = tables.stream()
                .filter(guest::hasNotSatAt)
                .map(table -> new ImmutablePair<>(scoreWithGuestInRound(table, guest, round), table))
                .sorted(Comparator.comparingDouble(Pair::getLeft)).collect(Collectors.toList());

        return randomise(tablesBySuitability.stream()
                .filter(pair -> pair.getLeft().equals(tablesBySuitability.get(0).getLeft()))
                .map(Pair::getRight)
                .collect(Collectors.toList()));
    }

    private double scoreWithGuestInRound(Table table, Guest guest, int round) {
        return Guest.calculateScore(guest, table.getGuestsAtTableInRound(round));
    }
}

package net.landj.tableplannerj.model;

import net.landj.tableplannerj.allocators.LeastGuestsRandomTableAllocator;
import net.landj.tableplannerj.allocators.LookAheadTableAllocator;
import net.landj.tableplannerj.allocators.NextTableAllocator;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public final class PlanFactory {
    private final int noOfSessions;
    private final List<String> tableNames;
    private final List<String> guestNames;
    private final NextTableAllocator nextTableAllocator;

    public final int getNoOfTables() {
        return this.tableNames.size();
    }

    public final int getNoOfGuests() {
        return this.guestNames.size();
    }

    public final int getIdealSeatingThreshold() {
        return getNoOfTables() * (noOfSessions - 1);
    }

    @NotNull
    public final TablePlan newPlan() {
        return new TablePlan(guestNames.stream().map(Guest::new).collect(Collectors.toList()),
                tableNames.stream().map(Table::new).collect(Collectors.toList()),
                noOfSessions,
                nextTableAllocator);
    }

    public final int getNoOfSessions() {
        return this.noOfSessions;
    }

    public PlanFactory(int noOfSessions,
                       List<String> tableNames,
                       List<String> guestNames,
                       NextTableAllocator nextTableAllocator) {
        this.noOfSessions = noOfSessions;
        this.tableNames = tableNames;
        this.guestNames = guestNames;
        if (nextTableAllocator != null) {
            this.nextTableAllocator = nextTableAllocator;
        } else {
            this.nextTableAllocator = getNoOfGuests() <= getIdealSeatingThreshold()
                    ? new LookAheadTableAllocator()
                    : new LeastGuestsRandomTableAllocator();
        }
    }
}

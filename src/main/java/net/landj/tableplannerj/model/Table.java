package net.landj.tableplannerj.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

import static java.util.stream.Collectors.summarizingInt;

public final class Table {
    private final HashMap<Integer, ArrayList<Guest>> guestsAtTableByRound;
    @NotNull
    private final String id;

    public final int getTotalGuestsAtTable() {
        return (int) guestsAtTableByRound.values().stream().collect(summarizingInt(ArrayList::size)).getSum();
    }

    public final int getNoOfGuestsAtTable(int round) {
        return this.getGuestsAtTableInRound(round).size();
    }

    public final void seatAGuest(@NotNull Guest guest, int round) {
        ArrayList<Guest> alreadyAtTable = getGuestsAtTableInRound(round);

        for (Guest alreadySeated : alreadyAtTable) {
            alreadySeated.greet(guest, round);
            guest.greet(alreadySeated, round);
        }

        alreadyAtTable.add(guest);
        guest.seatAt(this);
    }

    @NotNull
    public final String getSeatingInRound(int round) {
        return getGuestsAtTableInRound(round).toString();
    }

    @NotNull
    public final ArrayList<Guest> getGuestsAtTableInRound(int round) {
        return guestsAtTableByRound.computeIfAbsent(round, key -> new ArrayList<>());
    }

    @NotNull
    public String toString() {
        return this.id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Table table = (Table) o;

        return id.equals(table.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @NotNull
    public final String getId() {
        return this.id;
    }

    public Table(@NotNull String id) {
        this.id = id;
        this.guestsAtTableByRound = new HashMap<>();
    }
}
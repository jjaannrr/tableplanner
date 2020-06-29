package net.landj.tableplannerj.model;

import net.landj.tableplannerj.allocators.NextTableAllocator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.leftPad;

public final class Usher {
    private int round;
    private final List<Guest> guests;
    private final List<Table> tables;
    private final NextTableAllocator nextTableAllocator;

    public final void firstRound() {
        this.round = 1;
        for(int i = 0; i < guests.size(); i++) {
            this.tables.get(i % this.tables.size()).seatAGuest(this.guests.get(i), this.round);
        }

    }

    public final void nextRound() {
        this.round++;
        List<Guest> remaining = new ArrayList<>(guests);

        while (!remaining.isEmpty()) {
            Guest guest = remaining.remove(0);

            nextTableAllocator.nextTable(guest, tables, round).seatAGuest(guest, round);
        }
    }

    public final void printSeating() {
        System.out.println("By Table");
        for (int i = 1; i <= round; i++) {
            System.out.printf("\tRound: %d%n", i);
            for (Table table : tables) {
                System.out.printf("\t\tTable %s - %s%n", table, table.getSeatingInRound(i));
            }
        }
        System.out.println("By Guest");
        for (Guest guest : guests) {
            System.out.printf("\tGuest: %s (%s) - tables %s - %s%n",
                    leftPad(guest.toString(), 2),
                    guest.getStats(),
                    guest.getTables(),
                    guest.getOthersSummary());
        }
    }

    public Usher(@NotNull List<Guest> guests, @NotNull List<Table> tables, @NotNull NextTableAllocator nextTableAllocator) {
        this.guests = guests;
        this.tables = tables;
        this.nextTableAllocator = nextTableAllocator;
    }
}

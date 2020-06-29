package net.landj.tableplannerj.model;

import net.landj.tableplannerj.TablePlanner;
import net.landj.tableplannerj.allocators.LeastGuestsRandomTableAllocator;
import net.landj.tableplannerj.allocators.LookAheadTableAllocator;
import net.landj.tableplannerj.allocators.NextTableAllocator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;

import static java.lang.Math.abs;
import static java.lang.Math.max;

public final class TablePlan implements Runnable {
    private final ParameterStats guestScores;
    @NotNull
    private final ParameterStats followUps;
    private final ParameterStats diversities;
    private final Usher usher;
    private final List<Guest> guests;
    private final List<Table> tables;
    private final int noOfSessions;

    @NotNull
    public final ParameterStats getFollowUps() {
        return this.followUps;
    }

    /**
     * Table score - based on number of guests at a table over all rounds<p/>
     * Ideal score is 1 (each table saw the same number of guests).
     * @return table score
     */
    public final double getTableScore() {
        return 1.0 + tables.stream().mapToDouble(table -> abs(((double) table.getTotalGuestsAtTable()) / noOfSessions - ((double) guests.size()) / tables.size())).sum();
    }

    /**
     * Follow up score.<p/>
     * Ideal follow up score is 1 (no 2 guests follow each other up to the following table)
     * @return follow up score
     */
    public final double getFollowUpsScore() {
        return max(followUps.getMax() + (followUps.getMed() - followUps.getAvg()), 1.0);
    }

    /**
     * Score how many guests each guest meets.<p/>
     * Ideal score is 1 (when each guest meets the same number of guests).<br/>
     * Prefer plans with same diversity for each guest
     * @return number of other met by a guest
     */
    public final double getDiversityScore() {
        return 1.0 + (this.diversities.getMax() - this.diversities.getMin());
    }

    /**
     * Get plan rating.<p/>
     * Made up of {@link #getTableScore()}, {@link #getDiversityScore()}, average guest score and {@link #getTableScore()}<br/>
     * For ideal rating see
     * @return plan rating
     */
    public final double getRating() {
        return this.getFollowUpsScore() * this.getDiversityScore() * this.guestScores.getAvg() * this.getTableScore();
    }

    public void run() {
        this.usher.firstRound();
        for(int i = 1; i < noOfSessions; i++) {
            this.usher.nextRound();
        }

        this.calculateStats();
    }

    private void calculateStats() {
        guestScores.calculate(guests.stream().mapToDouble(Guest::getScore).toArray());
        diversities.calculate(guests.stream().mapToDouble(Guest::getDiversity).toArray());
        followUps.calculate(guests.stream().mapToDouble(Guest::getFollowUps).toArray());
    }

    public final void print() {
        System.out.printf("Rating: %.2f (%.2f * %.2f * %.2f * %.2f%n", getRating(), getFollowUpsScore(), getDiversityScore(), guestScores.getAvg(), getTableScore());
        this.usher.printSeating();
        this.printStats();
    }

    public final void outputToCsv(@NotNull File file) throws FileNotFoundException {
        try (PrintWriter it = new PrintWriter(file)) {
            // header
            it.print("Guests");
            for (int i = 1; i <= noOfSessions; i++) {
                it.printf(",Round %d", i);
            }
            it.println();
            // guests
            for (Guest guest : guests) {
                it.print(guest.getId());
                for (Table table : guest.getTables()) {
                    it.printf(",%s", table.getId());
                }
                it.println();
            }
        }
    }

    private void printStats() {
        System.out.println(guestScores);
        System.out.println(diversities);
        System.out.println(followUps);
    }

    public TablePlan(List<Guest> guests, List<Table> tables, int noOfSessions, NextTableAllocator nextTableAllocator) {
        this.guests = guests;
        this.tables = tables;
        this.noOfSessions = noOfSessions;
        this.guestScores = new ParameterStats(ParameterStats.Parameter.SCORE);
        this.followUps = new ParameterStats(ParameterStats.Parameter.FOLLOW_UPS);
        this.diversities = new ParameterStats(ParameterStats.Parameter.DIVERSITY);
        this.usher = new Usher(this.guests, this.tables, nextTableAllocator);
    }
}

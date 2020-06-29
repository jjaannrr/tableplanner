package net.landj.tableplannerj.model;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.pow;
import static java.util.stream.Collectors.summarizingDouble;
import static net.landj.tableplannerj.TablePlanner.BASE_GUEST_RATING;
import static org.apache.commons.lang3.StringUtils.leftPad;

public final class Guest {
    private final HashMap<Guest, List<Integer>> otherGuests;
    @NotNull
    private final ArrayList<Table> tables;
    private Double score;
    @NotNull
    private final String id;

    @NotNull
    public final ArrayList<Table> getTables() {
        return this.tables;
    }

    public final double getScore() {
        if (this.score == null) {
            this.score = calculateScore(this);
        }
        return score;
    }

    public final int getDiversity() {
        return this.otherGuests.size();
    }

    public final int getFollowUps() {
        int followUps = 0;
        List<List<Integer>> multipleMeetings = otherGuests.values().stream()
                .filter(meetings -> meetings.size() > 1)
                .collect(Collectors.toList());

        for (List<Integer> meetings : multipleMeetings) {
            for (int i = 0; i < meetings.size() - 1; i++) {
                if (meetings.get(i).equals(meetings.get(i + 1) - 1)) {
                    followUps++;
                }
            }
        }

        return followUps;
    }

    public final void greet(@NotNull Guest other, int round) {
        score = null;
        List<Integer> meetings = this.otherGuests.get(other);
        if (meetings != null) {
            meetings.add(round);
        } else {
            List<Integer> newList = new ArrayList<>();
            newList.add(round);
            this.otherGuests.put(other, newList);
        }

    }

    public final void seatAt(@NotNull Table table) {
        this.tables.add(table);
    }

    public final boolean hasNotSatAt(@NotNull Table table) {
        return !this.tables.contains(table);
    }

    public final int getMeetings(@NotNull Guest other) {
        return otherGuests.getOrDefault(other, Collections.emptyList()).size();
    }

    private String getPadStartId() {
        return leftPad(id, 2);
    }

    @NotNull
    public final String getOthersSummary() {
        StringBuilder sb = new StringBuilder();
        otherGuests.forEach((key, value) -> sb.append(key.getPadStartId()).append(' ').append(value).append(", "));
        return sb.length() > 0 ? sb.substring(0, sb.length() - 2) : sb.toString();
    }

    public String getStats() {
        return String.format("followUps=%d, diversity=%d, score=%.2f", getFollowUps(), getDiversity(), getScore());
    }

    @NotNull
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Guest guest = (Guest) o;

        return id.equals(guest.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @NotNull
    public final String getId() {
        return this.id;
    }

    public Guest(@NotNull String id) {
        this.id = id;
        this.otherGuests = new HashMap<>();
        this.tables = new ArrayList<>();
    }

    public static double calculateScore(@NotNull Guest guest) {
        return calculateScore(guest, guest.otherGuests.keySet());
    }

    /**
     * Calculate guest score.<p/>
     * average of meeting rating. Meeting rating gets higher as number of meetings between the same 2 guests goes up.
     *
     * @param guest  guest
     * @param others list of other guests {@code guest} has meet
     * @return guest score
     */
    public static double calculateScore(@NotNull Guest guest, @NotNull Collection<Guest> others) {
        return others.stream()
                .collect(summarizingDouble(other -> pow(BASE_GUEST_RATING, other.getMeetings(guest))))
                .getSum() / guest.getDiversity();
    }

}

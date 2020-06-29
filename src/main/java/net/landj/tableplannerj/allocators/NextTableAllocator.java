package net.landj.tableplannerj.allocators;

import net.landj.tableplannerj.model.Guest;
import net.landj.tableplannerj.model.Table;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

public abstract class NextTableAllocator {
    private final Random random = new Random();

    @NotNull
    public abstract Table nextTable(@NotNull Guest guest, @NotNull List<Table> tables, int round);

    public Table randomise(List<Table> pool) {
        return pool.get(random.nextInt(pool.size()));
    }
}

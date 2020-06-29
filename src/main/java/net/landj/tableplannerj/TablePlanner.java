package net.landj.tableplannerj;

import net.landj.tableplannerj.model.PlanFactory;
import net.landj.tableplannerj.model.TablePlan;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static java.lang.Math.log;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.IntStream.rangeClosed;

@Command(name = "tableplanner", version = "1.0-java")
public final class TablePlanner implements Runnable {
    public static final double BASE_GUEST_RATING = 2.0;
    public static final int FILE_SIZE_LIMIT = 1024;

    @Option(names = {"-t", "--tables"}, description = "Number of tables", defaultValue = "4")
    private int noOfTablesOption;

    @Option(names = {"-s", "--sessions"}, description = "Number of sessions", defaultValue = "4")
    private int noOfSessionsOption;

    @Option(names = {"-g", "--guests"}, description = "Number of people (minus hosts). Ignored if list of names is provided!", defaultValue = "16")
    private int noOfGuestsOption;

    @Option(names = {"-i", "--input"}, description = "Path to a files with list of names to seat")
    private File namesFileOption;

    @Option(names = {"-o", "--output"}, description = "Output file (CSV)")
    private File csvFileOption;

    @Option(names = {"-it", "--iterations"}, description = "Number of possible runs", defaultValue = "10000")
    private int noOfIterationsOption;

    @Option(names = {"-th", "--threads"}, description = "Number of calculation threads", defaultValue = "8")
    private int noOfThreadsOption;

    private final LinkedBlockingQueue<TablePlan> resultQueue = new LinkedBlockingQueue<>();
    private ExecutorService executorService;
    private PlanFactory planFactory;
    private int processedResults;
    private boolean solutionFound = false;

    public void run() {
        try {
            initialise();

            System.out.println("guests=" + planFactory.getNoOfGuests() + ", tables=" + planFactory.getNoOfTables() + ", sessions=" + planFactory.getNoOfSessions() + ", iterations=" + noOfIterationsOption);

            double start = System.currentTimeMillis();

            executorService.submit(new TablePlanner.PlansGenerator());

            TablePlan plan = this.processResults();
            long stop = System.currentTimeMillis();
            System.out.println("Result in: " + ((double) stop - start) / (double) 1000 + " (results processed: " + this.processedResults + ')');

            executorService.shutdownNow();
            if (plan != null) {
                plan.print();
                if (csvFileOption != null) {
                    plan.outputToCsv(csvFileOption);
                }
            } else {
                System.out.println("No suitable result found!");
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private void validateOptions() {
        StringBuilder violations = new StringBuilder();
        if (noOfTablesOption < 3 || noOfTablesOption > 5) {
            violations.append("between 3 and 5 tables are supported");
        }
        if (noOfSessionsOption < 2 || noOfSessionsOption > 5) {
            if (violations.length() > 0) violations.append('\n');
            violations.append("between 2 and 5 sessions are supported");
        }
        if (noOfGuestsOption < noOfTablesOption * 2 || noOfGuestsOption > 50) {
            if (violations.length() > 0) violations.append('\n');
            violations.append("At least enough people for 1 guest at a table");
        }
        if (namesFileOption != null && (
                !namesFileOption.exists()
                        || !namesFileOption.isFile()
                        || !namesFileOption.canRead()
                        || namesFileOption.length() > FILE_SIZE_LIMIT)) {
            if (violations.length() > 0) violations.append('\n');
            violations.append("Input file must exist and be readable with each file name on a separate line\"");
        }
        if (noOfIterationsOption < 100 || noOfIterationsOption > 1000000) {
            if (violations.length() > 0) violations.append('\n');
            violations.append("iterations between 100 and 1,000,000 are expected");
        }
        if (noOfThreadsOption < 2 || noOfThreadsOption > 16) {
            if (violations.length() > 0) violations.append('\n');
            violations.append("2 threads minimum are required");
        }
        if (violations.length() > 0) {
            throw new IllegalArgumentException(violations.toString());
        }
    }

    private void initialise() throws IOException {
        validateOptions();
        executorService = Executors.newFixedThreadPool(noOfThreadsOption);
        planFactory = initialisePlanFactory();
    }

    private PlanFactory initialisePlanFactory() throws IOException {
        List<String> tableNames;
        List<String> guestNames;

        if (namesFileOption != null) {
            List<String> names = Files.readAllLines(namesFileOption.toPath());
            if (names.size() < noOfTablesOption * 2 || names.size() > 50) {
                throw new IllegalArgumentException("Names for at least 1 host and 1 guest for each table are required and no more than 50 names");
            }
            tableNames = names.subList(0, noOfTablesOption);
            guestNames = names.subList(noOfTablesOption, names.size());
        } else {
            tableNames = rangeClosed(1, noOfTablesOption).mapToObj(Integer::toString).collect(Collectors.toList());
            guestNames = rangeClosed(1, noOfGuestsOption).mapToObj(Integer::toString).collect(Collectors.toList());
        }

        return new PlanFactory(noOfSessionsOption, tableNames, guestNames, null);
    }

    /**
     * How many times do we allow 2 guests to follow each other up to the following table.<p/>
     * Depending on number of guests and tables, it might be necessary for 2 to follow each other up to the next table.
     * If this is the case we don't the same 2 keep following each other up but to share the burden between other guests as well.
     * @return maximum number of follow ups we allow to accept
     */
    private int determineMaxFollowUps() {
        double logResult = log(noOfGuestsOption) / log(noOfTablesOption);
        int logResultInt = (int) logResult;
        return logResult - logResultInt > 0.0 ? logResultInt + 1 : logResultInt;
    }

    /**
     * Determine perfect score to allow terminate iterations early.<p/>
     * At the moment we know perfect score certainly for cases where the number of guests is up to the maximum where each guest can move around without seeing anyone twice.
     * @return perfect plan score
     */
    private double determinePerfectRating() {
        return planFactory.getNoOfGuests() == planFactory.getIdealSeatingThreshold()
                ? BASE_GUEST_RATING // all other plan ranking parameters should be their ideal 1
                : BASE_GUEST_RATING * 2; // all but table score should be at ideal 1. table score will be 2 as the number of guests is not the same at each table every round
    }

    private TablePlan processResults() throws InterruptedException {
        List<TablePlan> filteredPlans = new ArrayList<>();
        List<TablePlan> allPlans = new ArrayList<>();

        int maxFollowUps = determineMaxFollowUps();
        // don't rely on table score if number or tables and sessions is not aligned (table score filter below wasn't designed for that)
        boolean ignoreTableScore = noOfTablesOption - noOfSessionsOption >= 1;
        double perfectRating = determinePerfectRating();

        while (processedResults < noOfIterationsOption) {
            TablePlan plan = resultQueue.take();
            processedResults++;

            if (plan.getRating() == perfectRating) {
                // don't look any further, we found a perfect solution
                solutionFound = true;
                return plan;
            } else if (plan.getFollowUps().getMax() <= maxFollowUps && (ignoreTableScore || plan.getTableScore() == 1.0)) {
                // filter out plans where there are too many follow ups (over the threshold)
                // where there is ideally spread seating (all hosts see the same amount of guests)
                filteredPlans.add(plan);
            }

            allPlans.add(plan);
        }

        if (filteredPlans.size() > 0) {
            solutionFound = true;
            return filteredPlans.stream().min(comparingDouble(TablePlan::getRating)).get();
        } else {
            // this might happen if there is not enough iterations to get a chance to get to a nice result
            System.out.println("What?");
            return allPlans.stream().min(comparingDouble(TablePlan::getRating)).orElseThrow(() -> new IllegalStateException("There must be at least 1 plan"));
        }
    }

    public final class PlansGenerator implements Runnable {
        public void run() {
            for (int i = 0; i < noOfIterationsOption && !solutionFound; i++) {
                executorService.submit(() -> {
                    TablePlan plan = planFactory.newPlan();
                    plan.run();
                    resultQueue.offer(plan);
                });
            }

        }
    }
}

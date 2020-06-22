package net.landj.tableplan

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import net.landj.tableplan.ParameterStats.Parameter.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.*

const val NO_OF_GUESTS = 16
const val NO_OF_TABLES = 4
const val NO_OF_SESSIONS = 4
const val NO_OF_ITERATIONS = 10000
const val NO_OF_THREADS = 8
const val SAME_PERSON_PENALTY = 2.0
const val PERFECT_PLAN_RATING = SAME_PERSON_PENALTY
const val FILE_SIZE_LIMIT = 1024

fun Double.format(digits: Int) = "%.${digits}f".format(this)

class Guest(val id: String) {
    private val otherGuests = HashMap<Guest, MutableList<Int>>()
    val tables = ArrayList<Table>()
    private var _score: Double? = null

    val score: Double
        get() {
            if (_score == null) {
                _score = calculateScore(this)
            }
            return _score as Double
        }

    val diversity: Int
        get() {
            return otherGuests.size
        }

    val followUps: Int
        get() {
            var followUps = 0
            otherGuests
                    .filter { (_, meetings) -> meetings.size > 1 }
                    .forEach { (_, meetings) ->
                        for (i in 0 until meetings.size - 1) {
                            if (meetings[i] == meetings[i + 1] - 1) {
                                followUps++
                            }
                        }
                    }
            return followUps
        }

    fun greet(other: Guest, round: Int) {
        _score = null
        val meetings = otherGuests[other]
        if (meetings != null) {
            meetings.add(round)
        } else {
            otherGuests[other] = mutableListOf(round)
        }
    }

    fun seatAt(table: Table) {
        tables.add(table)
    }

    fun hasAlreadySetAt(table: Table): Boolean {
        return tables.contains(table)
    }

    fun getMeetings(other: Guest): Int {
        return otherGuests.getOrDefault(other, emptyList<Guest>()).size
    }

    fun getOtherGuestsSummary(): String {
        return otherGuests.map { other -> "${other.key.id.padStart(2)} ${other.value}" }.joinToString()
    }

    override fun toString(): String {
        return "${id.padStart(2)} (followUps=$followUps, diversity=$diversity, score=${score.format(2)})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Guest

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        fun calculateScore(guest: Guest): Double {
            return calculateScore(guest, guest.otherGuests.keys)
        }

        fun calculateScore(guest: Guest, others: Collection<Guest>): Double {
            return others.sumByDouble { other ->
                val meetings = other.getMeetings(guest).toDouble()
                return SAME_PERSON_PENALTY.pow(meetings)
            }
        }
    }
}

class Table(val id: String) {
    private val guestsAtTableByRound = HashMap<Int, ArrayList<Guest>>()

    val totalGuestsAtTable: Int
        get() = guestsAtTableByRound.map { (_, guests) -> guests.size }.sum()

    fun getNoOfGuestsAtTable(round: Int): Int {
        return getGuestsAtTableInRound(round).size
    }

    fun seatAGuest(guest: Guest, round: Int) {
        val alreadyAtTable = getGuestsAtTableInRound(round)
        for (alreadySeated in alreadyAtTable) {
            alreadySeated.greet(guest, round)
            guest.greet(alreadySeated, round)
        }
        alreadyAtTable.add(guest)
        guest.seatAt(this)
    }

    fun getSeatingInRound(round: Int): String {
        return getGuestsAtTableInRound(round).toString()
    }

    fun getGuestsAtTableInRound(round: Int): ArrayList<Guest> {
        return guestsAtTableByRound.getOrPut(round) { ArrayList() }
    }

    override fun toString(): String {
        return id
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Table

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

interface NextTableAllocator {
    fun nextTable(guest: Guest, tables: List<Table>, round: Int): Table
}

class LookAheadTableAllocator : NextTableAllocator {
    override fun nextTable(guest: Guest, tables: List<Table>, round: Int): Table {
        val tablesBySuitability = tables
                .filter { table -> !guest.hasAlreadySetAt(table) }
                .map { table -> Pair(scoreWithGuestInRound(table, guest, round), table) }
                .sortedBy { entry -> entry.first }
        return tablesBySuitability.filter { entry -> entry.first == tablesBySuitability[0].first }.random().second
    }

    private fun scoreWithGuestInRound(table: Table, guest: Guest, round: Int): Double {
        return Guest.calculateScore(guest, table.getGuestsAtTableInRound(round))
    }

}

@Suppress("unused")
class RandomTableAllocator : NextTableAllocator {
    override fun nextTable(guest: Guest, tables: List<Table>, round: Int): Table {
        return tables.filter { table -> !guest.hasAlreadySetAt(table) }.random()
    }
}

@Suppress("unused")
class LeastGuestsTableAllocator : NextTableAllocator {
    override fun nextTable(guest: Guest, tables: List<Table>, round: Int): Table {
        val suitableTables = tables.filter { table -> !guest.hasAlreadySetAt(table) }
        return suitableTables.minBy { table -> table.getNoOfGuestsAtTable(round) } ?: suitableTables.first()
    }
}

class LeastGuestsRandomTableAllocator : NextTableAllocator {
    override fun nextTable(guest: Guest, tables: List<Table>, round: Int): Table {
        val tablesBySuitability = tables
                .filter { table -> !guest.hasAlreadySetAt(table) }
                .map { table -> Pair(table.getNoOfGuestsAtTable(round), table) }
                .sortedBy { entry -> entry.first }

        return tablesBySuitability.filter { entry -> entry.first == tablesBySuitability[0].first }.random().second
    }
}

class Usher(private val guests: List<Guest>, private val tables: List<Table>, private val nextTableAllocator: NextTableAllocator) {
    private var round = 0
    private var seatedInCurrentRound = ArrayList<Guest>()

    fun firstRound() {
        round = 1
        for (i in guests.indices) {
            tables[i % tables.size].seatAGuest(guests[i], round)
        }
    }

    fun nextRound() {
        round++
        seatedInCurrentRound.clear()

        val remaining = guests.toMutableList()

        while (remaining.isNotEmpty()) {
            val guest = remaining.first()

            nextTableAllocator.nextTable(guest, tables, round).seatAGuest(guest, round)

            remaining.remove(guest)
            seatedInCurrentRound.add(guest)
        }
    }

    fun printSeating() {
        println("By Table")
        for (i in 1..round) {
            println("\tRound: $i")
            for (table in tables) {
                println("\t\tTable $table - ${table.getSeatingInRound(i)}")
            }
        }
        println("By Guest")
        for (guest in guests) {
            println("\tGuest: $guest - tables ${guest.tables} - ${guest.getOtherGuestsSummary()}")
        }

    }
}

class ParameterStats(private val parameter: Parameter) {
    enum class Parameter {
        Score, Diversity, FollowUps
    }

    private var _min: Double = 0.0
    private var _max: Double = 0.0
    private var _avg: Double = 0.0
    private var _med: Double = 0.0

    val min: Double
        get() = _min

    val max: Double
        get() = _max
    val avg: Double
        get() = _avg

    val med: Double
        get() = _med

    fun calculate(values: List<Double>) {
        _min = values.min() ?: 0.0
        _max = values.max() ?: Double.MAX_VALUE
        _avg = values.average()
        _med = if (values.isNotEmpty()) values[values.size / 2] else 0.0
    }

    override fun toString(): String {
        return "$parameter: min = ${min.format(2)}, max = ${max.format(2)}, avg = ${avg.format(2)}, med = ${med.format(2)}"
    }
}

@Suppress("MemberVisibilityCanBePrivate")
class TablePlan(
        private val guests: List<Guest>,
        private val tables: List<Table>,
        private val noOfSessions: Int
) : Runnable {
    private val guestScores = ParameterStats(Score)
    val followUps = ParameterStats(FollowUps)
    private val diversities = ParameterStats(Diversity)

    private val nextTableAllocator = if (guests.size <= tables.size * (noOfSessions - 1)) LookAheadTableAllocator() else LeastGuestsRandomTableAllocator()

    private val usher = Usher(guests, tables, nextTableAllocator)

    val tableScore: Double
        get() = 1.0 + tables.map { table -> abs(table.totalGuestsAtTable.toDouble().div(noOfSessions) - guests.size.toDouble().div(tables.size)) }.sum()

    val followUpsScore: Double
        get() = max(followUps.max + (followUps.med - followUps.avg).absoluteValue, 1.0)

    val diversityScore: Double
        get() = 1.0 + (diversities.max - diversities.min)

    val rating: Double
        get() {
            return followUpsScore * diversityScore * guestScores.avg * tableScore
        }

    override fun run() {
        usher.firstRound()
        for (i in 1 until noOfSessions) {
            usher.nextRound()
        }

        calculateStats()
    }

    private fun calculateStats() {
        guestScores.calculate(guests.map { guest -> guest.score })
        diversities.calculate(guests.map { guest -> guest.diversity.toDouble() })
        followUps.calculate(guests.map { guest -> guest.followUps.toDouble() })
    }

    fun print() {
        println("Rating: ${rating.format(2)} (${followUpsScore.format(2)} * ${diversityScore.format(2)} * ${guestScores.avg.format(2)} * ${tableScore.format(2)})")
        usher.printSeating()
        printStats()
    }

    fun outputToCsv(file: File) {
        file.printWriter().use {
            // header
            it.print("Guests")
            for (i in 1..noOfSessions) {
                it.print(",Round $i")
            }
            it.println()
            // guests
            for (guest in guests) {
                it.print(guest.id)
                for (table in guest.tables) {
                    it.print(",${table.id}")
                }
                it.println()
            }
        }
    }

    private fun printStats() {
        println(guestScores)
        println(diversities)
        println(followUps)
    }

}

class PlanFactory(val noOfSessions: Int,
                  private val tableNames: List<String>,
                  private val guestNames: List<String>) {

    val noOfTables: Int
        get() = tableNames.size

    val noOfGuests: Int
        get() = guestNames.size

    fun newPlan(): TablePlan {
        return TablePlan(guestNames.map { name -> Guest(name) }, tableNames.map { name -> Table(name) }, noOfSessions)
    }
}

class TablePlaner : CliktCommand() {
    private val noOfTablesOption: Int by option("-t", "--tables", help = "Number of tables").int().default(NO_OF_TABLES)
            .validate { require(it in 3..5) { "between 3 and 5 sessions are supported" } }
    private val noOfSessionsOption: Int by option("-s", "--sessions", help = "Number of sessions").int().default(NO_OF_SESSIONS)
            .validate { require(it in 2..5) { "between 2 and 5 sessions are supported" } }
    private val noOfGuestsOption: Int by option("-g", "--guests", help = "Number of people (minus hosts). Ignored if list of names is provided!").int().default(NO_OF_GUESTS)
            .validate { require(it >= noOfTablesOption * 2 && it <= 50) { "At least enough people for 1 guest at a table" } }
    private val namesFileOption: File? by option("-i", "--input", help = "Path to a files with list of names to seat").file()
            .validate { require(it.exists() && it.isFile && it.canRead() && it.length() < FILE_SIZE_LIMIT) { "Input file must exist and be readable with each file name on a separate line" } }
    private val csvFileOption: File? by option("-o", "--output", help = "Output file (CSV)").file()
    private val noOfIterationsOption: Int by option("-it", "--iterations", help = "Number of calculation runs").int().default(NO_OF_ITERATIONS)
            .validate { require(it in 100..1000000) { "iterations between 100 and 1,000,000 are expected" } }
    private val noOfThreadsOption: Int by option("-th", "--threads", help = "Number of calculation threads").int().default(NO_OF_THREADS)
            .validate { require(it in 2..16) { "2 threads minimum are required" } }

    private val executorService: ExecutorService by lazy { Executors.newFixedThreadPool(noOfThreadsOption) }
    private val resultQueue = LinkedBlockingQueue<TablePlan>()

    private val planFactory: PlanFactory by lazy { initialisePlanFactory() }

    private var processedResults = 0

    var solutionFound: Boolean = false

    override fun run() {
        try {
            println("guests=${planFactory.noOfGuests}, tables=${planFactory.noOfTables}, sessions=${planFactory.noOfSessions}, iterations=$noOfIterationsOption")

            val start = System.currentTimeMillis().toDouble()

            executorService.submit(PlansGenerator())

            val plan = processResults()

            val stop = System.currentTimeMillis()
            println("Result in: ${(stop - start) / 1000} (results processed: $processedResults)")

            executorService.shutdownNow()

            if (plan != null) {
                plan.print()
                if (csvFileOption != null) {
                    plan.outputToCsv(csvFileOption as File)
                }
            } else {
                println("No suitable result found!")
            }
        } catch (e: Exception) {
            println("ERROR: ${e.message}")
        }
    }

    private fun initialisePlanFactory(): PlanFactory {
        val tableNames: List<String>
        val guestNames: List<String>

        if (namesFileOption != null) {
            val names = (namesFileOption as File).readLines().map { line -> line.trim() }.filter { line -> line.isNotEmpty() }
            if (names.size !in (noOfTablesOption * 2)..50) {
                throw IllegalArgumentException("Names for at least 1 host and 1 guest for each table are required and no more than 50 names")
            }
            tableNames = names.subList(0, noOfTablesOption)
            guestNames = names.subList(noOfTablesOption, names.size)
        } else {
            tableNames = List(noOfTablesOption) { it.plus(1).toString() }
            guestNames = List(noOfGuestsOption) { it.plus(1).toString() }
        }

        return PlanFactory(noOfSessionsOption, tableNames, guestNames)
    }

    private fun determineMaxFollowUps(): Int {
        val logResult = (ln(noOfGuestsOption.toDouble()) / ln(noOfTablesOption.toDouble()))
        val logResultInt = logResult.toInt()
        return if (logResult - logResultInt > 0.0) logResultInt + 1 else logResultInt
    }

    private fun processResults(): TablePlan? {
        val filteredPlans: MutableList<TablePlan> = mutableListOf()
        val allPlans: MutableList<TablePlan> = mutableListOf()

        val maxFollowUps = determineMaxFollowUps()
        val ignoreTableScore = noOfTablesOption - noOfSessionsOption >= 1

        println("max follow ups = $maxFollowUps, ignore table score = $ignoreTableScore")

        while (processedResults < noOfIterationsOption) {
            val plan = resultQueue.take()
            processedResults++

            if (plan.rating == PERFECT_PLAN_RATING) {
                solutionFound = true
                return plan
            } else if (plan.followUps.max <= maxFollowUps && (ignoreTableScore || plan.tableScore == 1.0)) {
                filteredPlans.add(plan)
            }

            allPlans.add(plan)
        }

        return if (filteredPlans.size > 0) {
            solutionFound = true
            filteredPlans.minBy { p -> p.rating }
        } else {
            println("What?")
            allPlans.minBy { p -> p.rating }
        }
    }

    inner class PlansGenerator : Runnable {
        override fun run() {
            for (i in 0 until noOfIterationsOption) {
                if (solutionFound) break

                executorService.submit {
                    val plan = planFactory.newPlan()
                    plan.run()
                    resultQueue.offer(plan)
                }
            }
        }

    }

}

fun main(args: Array<String>) {
    TablePlaner().main(args)
}
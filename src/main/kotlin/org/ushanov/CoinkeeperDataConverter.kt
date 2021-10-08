package org.ushanov

import org.ushanov.Constants.ANOTHER_CURRENCY
import org.ushanov.Constants.CATEGORY
import org.ushanov.Constants.CURRENCY
import org.ushanov.Constants.DATE_NEW
import org.ushanov.Constants.DATE_OLD
import org.ushanov.Constants.FROM
import org.ushanov.Constants.NOTE
import org.ushanov.Constants.REPEAT
import org.ushanov.Constants.SPENDING
import org.ushanov.Constants.SUM_ANOTHER_CURRENCY
import org.ushanov.Constants.TAGS
import org.ushanov.Constants.TO
import org.ushanov.Constants.TYPE
import org.ushanov.Constants.VALUE
import org.ushanov.Constants.VALUE_RUB
import org.apache.commons.csv.*
import java.io.*
import java.math.BigDecimal
import java.time.*


fun main() {
  val csvRecords = read().toList()
  val datesToRecords = groupByDates(csvRecords)
  val dailyRecords = computeDailyRecords(datesToRecords)
  val monthlyRecords = computeMonthlyRecords(dailyRecords)

  val records = csvRecords.map { Record.of(it) }
  writeRecords(records)
  writeMonthlyRecords(monthlyRecords)
  writeMonthlyCategoryStatistics(monthlyRecords, "07.2020")
}

private fun read(): CSVParser {
  val file: Reader = FileReader("$pathPrefix${sep}data.csv")
  return CSVFormat.DEFAULT
    .withHeader(DATE_OLD, TYPE, FROM, TO, TAGS, VALUE, CURRENCY, SUM_ANOTHER_CURRENCY, ANOTHER_CURRENCY, REPEAT, NOTE)
    .withFirstRecordAsHeader()
    .parse(file)
}

private fun writeRecords(records: List<Record>) {
  val out = FileWriter("$pathPrefix${sep}records.csv")
  CSVPrinter(
    out,
    CSVFormat.DEFAULT
      .withHeader(DATE_NEW, VALUE_RUB, TYPE, FROM, TO, NOTE)
      .withDelimiter(';')
  ).use { printer ->
    records.forEach { (date, type, from, to, value, note) ->
      if (date.month == Month.SEPTEMBER && date.year == 2021)
        printer.printRecord(date.printable(), value, type.printable(), from, to, note)
    }
  }
}

private fun writeMonthlyRecords(monthlyRecords: List<MonthlyRecord>) {
  val out = FileWriter("$pathPrefix${sep}monthly_records.csv")

  CSVPrinter(out, CSVFormat.DEFAULT.withHeader(
    DATE_NEW,
    OPERATION.CONSUMPTION.printable(),
    OPERATION.INCOME.printable(),
    OPERATION.TRANSACTION.printable(),
    SPENDING
  )).use {
    monthlyRecords.forEach { (date, consumption, income, transaction, spending) ->
      it.printRecord(date.monthYearPrintable(), consumption, income, transaction, spending)
    }
  }
}

private fun writeMonthlyCategoryStatistics(monthlyRecords: List<MonthlyRecord>, monthYear: String) {
  val out = FileWriter("$pathPrefix${sep}monthly_category_statistics.csv")

  CSVPrinter(out, CSVFormat.DEFAULT
    .withHeader(CATEGORY, OPERATION.CONSUMPTION.printable())
    .withDelimiter(';'))
    .use {
      val monthlyRecord = monthlyRecords.find { it.month == monthYear } ?: throw IllegalStateException("Unknown monthYear for printing")
      coinkeeperCategories.forEach { category ->
        val categoryConsumption = monthlyRecord.spending[category]
        it.printRecord(category, categoryConsumption)
      }
    }
}

private fun groupByDates(csvRecordsList: List<CSVRecord>): Map<LocalDate, List<Record>> {
  fun MutableMap<LocalDate, List<Record>>.flush(prevDate: LocalDate, recordsPerDate: List<Record>) {
    this[prevDate] = recordsPerDate.toMutableList() // copy, because recordsPerDate is cleared further
  }

  val datesToRecords = mutableMapOf<LocalDate, List<Record>>()
  val recordsForDate = mutableListOf<Record>()

  var prevDate = csvRecordsList.first()[DATE_OLD].date()

  csvRecordsList.forEach { csvRecord ->
    val date = csvRecord[DATE_OLD].date()

    if (date != prevDate) {
      datesToRecords.flush(prevDate, recordsForDate)
      recordsForDate.clear()
      recordsForDate += Record.of(csvRecord)
      prevDate = date
    } else {
      recordsForDate += Record.of(csvRecord)
    }
  }

  datesToRecords.flush(prevDate, recordsForDate)

  return datesToRecords
}

private fun computeDailyRecords(datesToRecords: Map<LocalDate, List<Record>>) = datesToRecords.map { (date, records) ->
  val spending = mutableMapOf<String, BigDecimal>()

  coinkeeperCategories.forEach { category ->
    val categorySpending = records
      .filter { it.to == category }
      .map { record ->
        if (record.operation != OPERATION.CONSUMPTION) {
          throw IllegalStateException("Found record $record with consumption category and non-consumption operation")
        }
        record.value
      }
      .takeIf { it.isNotEmpty() }
      ?.sum()
      ?: BigDecimal.ZERO

    spending[category] = categorySpending
  }

  DailyRecord(
    date = date,
    consumption = records.sumValuesFor(OPERATION.CONSUMPTION),
    income = records.sumValuesFor(OPERATION.INCOME),
    transaction = records.sumValuesFor(OPERATION.TRANSACTION),
    spending
  )
}

private fun computeMonthlyRecords(dailyRecords: List<DailyRecord>): List<MonthlyRecord> {
  fun LocalDate.monthAndYearString(): String = "${monthValue.toString().padStart(2, '0')}.$year"

  // monthYear is "06.1991"
  // toSet() is because otherwise there will be multiple similar entries: 29.06.1991, 30.06.1991 -> [06.1991, 06.1991]
  val monthsYears = dailyRecords.map { it.date.monthAndYearString() }.toSet()
  println("monthsYears")
  println(monthsYears)

  return monthsYears.map { monthYear ->
    val dailyRecordsForMonth = dailyRecords.filter { it.date.monthAndYearString() == monthYear }

    val spending = dailyRecordsForMonth
      .map { it.spending }
      .reduce { acc, nextSpending ->
        val result = mutableMapOf<String, BigDecimal>()
        result.putAll(acc)

        nextSpending.forEach { (key, value) ->
          val accValue = result[key]
          result[key] = accValue?.let { it + value } ?: value
        }

        result
      }

    MonthlyRecord(
      month = monthYear,
      consumption = dailyRecordsForMonth.map { it.consumption }.sum(),
      income = dailyRecordsForMonth.map { it.income }.sum(),
      transaction = dailyRecordsForMonth.map { it.transaction }.sum(),
      spending
    )
  }
}

private fun List<Record>.sumValuesFor(operation: OPERATION) = this
  .filter { it.operation == operation }
  .map { it.value }
  .takeIf { it.isNotEmpty() }
  ?.sum()
  ?: BigDecimal.ZERO

private fun List<BigDecimal>.sum() = reduce { acc, next -> acc + next }

private val sep: String = File.separator

private val pathPrefix = "src${sep}main${sep}resources${sep}csv"
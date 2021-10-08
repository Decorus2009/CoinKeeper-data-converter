package org.ushanov

import org.ushanov.Constants.DATE_OLD
import org.ushanov.Constants.FROM
import org.ushanov.Constants.NOTE
import org.ushanov.Constants.TO
import org.ushanov.Constants.TYPE
import org.ushanov.Constants.VALUE
import org.apache.commons.csv.CSVRecord
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

data class Record(
  val date: LocalDate,
  val operation: OPERATION,
  val from: String,
  val to: String,
  var value: BigDecimal,
  val note: String
) {
  companion object {
    fun of(csvRecord: CSVRecord) = with(csvRecord) {
      Record(
        date = get(DATE_OLD).date(),
        operation = operation(),
        from = get(FROM),
        to = get(TO),
        value = get(VALUE).replace(",", ".").toDouble().toBigDecimal(),
        note = get(NOTE)
      ).also { it.normalize() }
    }

    private fun CSVRecord.operation(): OPERATION {
      val type = this[TYPE]
      val from = this[FROM]

      if (from in setOf("ALM", "ФТИ", "Вклад", "Другое", "Cashback", "Mining", "Биржа")) {
        return OPERATION.INCOME
      }

      return OPERATION.of(type)
    }
  }

  fun normalize() {
    if (operation == OPERATION.CONSUMPTION) {
      value.multiply((-1).toBigDecimal())
    }
  }

  override fun toString() = "Record(date=${date.printable()}, op=${operation.name.toLowerCase().capitalize()}, from=$from, to=$to, value=$value)"
}

data class DailyRecord(
  val date: LocalDate,
  val consumption: BigDecimal,
  val income: BigDecimal,
  val transaction: BigDecimal,
  val spending: Map<String, BigDecimal> // category -> all spending for this category during a given day
) {
  override fun toString() =
    "DailyRecord(date=${date.printable()}, consumption=$consumption, income=$income, transaction=$transaction, spending=$spending)"
}

data class MonthlyRecord(
  val month: String,
  val consumption: BigDecimal,
  val income: BigDecimal,
  val transaction: BigDecimal,
  val spending: Map<String, BigDecimal> // category -> all spending for this category during a given month
)

enum class OPERATION {
  CONSUMPTION, INCOME, TRANSACTION;

  companion object {
    fun of(operation: String) = when (operation) {
      "Расход" -> CONSUMPTION
      "Доход" -> INCOME
      "Перевод" -> TRANSACTION
      else -> throw IllegalStateException("Unknown operation $operation")
    }
  }

  fun printable(): String = when (this) {
    CONSUMPTION -> "Расход"
    INCOME -> "Доход"
    TRANSACTION -> "Перевод"
  }
}


fun String.date() = LocalDate.parse(this, dateFormat)

fun LocalDate.printable() = dateFormat.format(this)

fun String.monthYearPrintable(): String {
  val (monthNumber, year) = split('.')
  val month = when (monthNumber) {
    "01" -> "Январь"
    "02" -> "Февраль"
    "03" -> "Март"
    "04" -> "Апрель"
    "05" -> "Май"
    "06" -> "Июнь"
    "07" -> "Июль"
    "08" -> "Август"
    "09" -> "Сентябрь"
    "10" -> "Октябрь"
    "11" -> "Ноябрь"
    "12" -> "Декабрь"
    else -> throw IllegalArgumentException("Unknown monthNumber: $monthNumber")
  }

  return "$month $year"
}

object Constants {
  val DATE_OLD = "Данные"
  val DATE_NEW = "Дата"
  val TYPE = "Тип"
  val FROM = "Из"
  val TO = "В"
  val VALUE = "Сумма"
  val VALUE_RUB = "Сумма, ₽"
  val SPENDING = "Траты по категориям"
  val TAGS = "Метки"
  val CURRENCY = "Валюта"
  val SUM_ANOTHER_CURRENCY = "Сумма в др.валюте"
  val ANOTHER_CURRENCY = "Др.валюта"
  val REPEAT = "Повторение"
  val NOTE = "Заметка"
  val CATEGORY = "Категория"
}

val coinkeeperCategories = setOf(
  "Комиссии (black hole)",
  "Дом",
  "Продукты",
  "Квартплата/Интернет",
  "Еда",
  "Путешествия",
  "Транспорт",
  "Мобильный",
  "Mining",
  "Charity",
  "Cryptocurrency",
  "Другое",
  "Спорт",
  "Кино/театр",
  "Вещи",
  "Музыка",
  "Развлечения",
  "Медицина",
)

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.US)

package com.salat.times.util

import java.time.DayOfWeek
import java.time.LocalDate

object ArabicNames {

    private val hijriMonths = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الثاني",
        "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
        "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    /** month: 1..12 */
    fun hijriMonthName(month: Int): String =
        if (month in 1..12) hijriMonths[month - 1] else ""

    private val weekDays = mapOf(
        DayOfWeek.SATURDAY to "السبت",
        DayOfWeek.SUNDAY to "الأحد",
        DayOfWeek.MONDAY to "الإثنين",
        DayOfWeek.TUESDAY to "الثلاثاء",
        DayOfWeek.WEDNESDAY to "الأربعاء",
        DayOfWeek.THURSDAY to "الخميس",
        DayOfWeek.FRIDAY to "الجمعة"
    )

    fun weekDayName(date: LocalDate): String = weekDays[date.dayOfWeek] ?: ""

    private val gregMonths = arrayOf(
        "جانفي", "فيفري", "مارس", "أفريل", "ماي", "جوان",
        "جويلية", "أوت", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    fun gregorianDateStr(date: LocalDate): String {
        return "${date.dayOfMonth} ${gregMonths[date.monthValue - 1]} ${date.year}"
    }

    /** Chiffres latins demandes par l'utilisateur (pas de conversion vers eastern-arabic). */
    fun toArabicDigits(input: String): String = input
}

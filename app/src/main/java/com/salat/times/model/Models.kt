package com.salat.times.model

/** Horaires des 6 temps (Fajr, Chourouk, Dhohr, Asr, Maghreb, Isha) pour un jour, format "HH:mm". */
data class DayTimes(
    val fa: String?,
    val ch: String?,
    val do_: String?,
    val as_: String?,
    val ma: String?,
    val is_: String?
)

/** Une ligne de la table horaires (source principale, annee hijri). */
data class HoraireEntry(
    val hy: Int,
    val hm: Int,
    val hd: Int,
    val g: String, // date gregorienne "yyyy-MM-dd"
    val times: DayTimes
)

/** Une ligne de la table ref_hor (fallback, par jour-de-l'annee gregorien). */
data class RefHorEntry(
    val doy: Int,
    val times: DayTimes
)

/** Une tranche de delta pour une ville (generique : supporte futures tranches infra-mensuelles). */
data class VilleDelta(
    val hy: Int,
    val hm: Int,
    val du: Int, // jour hijri de debut de tranche dans le mois
    val au: Int, // jour hijri de fin de tranche dans le mois
    val dFa: Int,
    val dCh: Int,
    val dDo: Int,
    val dAs: Int,
    val dMa: Int,
    val dIs: Int
)

data class Ville(
    val id: Int,
    val nom: String,
    val deltas: List<VilleDelta>
)

/** Resultat final calcule pour un jour donne, deja ajuste selon la ville. */
data class ComputedDay(
    val gregorianDate: java.time.LocalDate,
    val hijriYear: Int?,   // null si fallback ref_hor (annee hijri inconnue)
    val hijriMonth: Int?,  // 1..12, null si fallback
    val hijriDay: Int?,    // null si fallback
    val fajr: String?,
    val chourouk: String?,
    val dhohr: String?,
    val asr: String?,
    val maghreb: String?,
    val isha: String?
)

enum class PrayerKey { FAJR, DHOHR, ASR, MAGHREB, ISHA }

data class PrayerSlot(
    val key: PrayerKey,
    val label: String,
    val time: String? // "HH:mm"
)

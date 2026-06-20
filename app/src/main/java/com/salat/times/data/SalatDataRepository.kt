package com.salat.times.data

import android.content.Context
import android.os.Environment
import com.salat.times.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Charge les donnees de prieres (par defaut depuis assets/salat_data.json, embarque
 * et donc 100% offline). L'utilisateur peut aussi importer un fichier JSON de
 * remplacement depuis /storage/emulated/0/SalatAthan/salat_data.json (voir
 * PrefsManager.useImportedData) ; dans ce cas ce fichier externe est utilise a la
 * place, et peut etre annule a tout moment pour revenir au fichier d'origine.
 *
 * Logique de resolution des horaires (computeDay) :
 *  1. Si la date est dans la plage couverte par "horaires" (annee hijri fournie),
 *     on utilise cette source -> on a hy/hm/hd exacts.
 *  2. Sinon (avant/apres), fallback sur "ref_hor" par jour-de-l'annee gregorien -> Alger
 *     uniquement, pas de mois hijri disponible.
 *  3. Si une ville != Alger est selectionnee et qu'on est en cas (1) [on a hy/hm/hd],
 *     on cherche dans villes[].deltas la tranche (hy,hm) dont [du,au] contient hd,
 *     et on applique les deltas en minutes a chaque horaire.
 *     En cas (2) (fallback), aucun delta de ville n'est applique (donnees hijri absentes).
 */
class SalatDataRepository private constructor(
    private val horairesByDate: Map<LocalDate, HoraireEntry>,
    private val refHorByDoy: Map<Int, RefHorEntry>,
    val villes: List<Ville>,
    val horaireRange: Pair<LocalDate, LocalDate>
) {

    fun getVilleById(id: Int): Ville? = villes.find { it.id == id }

    /** Calcule les horaires resolus (avec ajustement ville) pour une date donnee. */
    fun computeDay(date: LocalDate, villeId: Int): ComputedDay {
        val primary = horairesByDate[date]
        if (primary != null) {
            val ville = getVilleById(villeId)
            val delta = ville?.deltas?.find {
                it.hy == primary.hy && it.hm == primary.hm &&
                    primary.hd in it.du..it.au
            }
            return ComputedDay(
                gregorianDate = date,
                hijriYear = primary.hy,
                hijriMonth = primary.hm,
                hijriDay = primary.hd,
                fajr = applyDelta(primary.times.fa, delta?.dFa ?: 0),
                chourouk = applyDelta(primary.times.ch, delta?.dCh ?: 0),
                dhohr = applyDelta(primary.times.do_, delta?.dDo ?: 0),
                asr = applyDelta(primary.times.as_, delta?.dAs ?: 0),
                maghreb = applyDelta(primary.times.ma, delta?.dMa ?: 0),
                isha = applyDelta(primary.times.is_, delta?.dIs ?: 0)
            )
        }

        // Fallback ref_hor (Alger uniquement, par jour-de-l'annee gregorien)
        val doy = date.dayOfYear.coerceIn(1, 366)
        val ref = refHorByDoy[doy] ?: refHorByDoy[366] ?: refHorByDoy.values.firstOrNull()
        return ComputedDay(
            gregorianDate = date,
            hijriYear = null,
            hijriMonth = null,
            hijriDay = null,
            fajr = ref?.times?.fa,
            chourouk = ref?.times?.ch,
            dhohr = ref?.times?.do_,
            asr = ref?.times?.as_,
            maghreb = ref?.times?.ma,
            isha = ref?.times?.is_
        )
    }

    private fun applyDelta(time: String?, deltaMinutes: Int): String? {
        if (time == null || deltaMinutes == 0) return time
        return try {
            val parts = time.split(":")
            var totalMin = parts[0].toInt() * 60 + parts[1].toInt() + deltaMinutes
            totalMin = ((totalMin % 1440) + 1440) % 1440
            val h = totalMin / 60
            val m = totalMin % 60
            "%02d:%02d".format(h, m)
        } catch (e: Exception) {
            time
        }
    }

    companion object {
        private const val ASSET_FILE = "salat_data.json"
        const val IMPORT_FILE_NAME = "salat_data.json"
        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        @Volatile private var instance: SalatDataRepository? = null

        /** Chemin du fichier d'import attendu sur le stockage externe. */
        fun importFile(): File =
            File(Environment.getExternalStorageDirectory(), "SalatAthan/$IMPORT_FILE_NAME")

        fun get(context: Context): SalatDataRepository {
            return instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }
        }

        /** Force un rechargement (apres import ou annulation d'import). */
        fun reload(context: Context): SalatDataRepository {
            synchronized(this) {
                val fresh = build(context)
                instance = fresh
                return fresh
            }
        }

        /**
         * Tente de parser un texte JSON candidat (verification de structure minimale).
         * Leve une exception si le format est invalide -> permet a l'appelant (import UI)
         * d'afficher un message d'erreur sans casser les donnees existantes.
         */
        fun validateJsonText(text: String) {
            val root = JSONObject(text)
            require(root.has("horaires")) { "champ 'horaires' manquant" }
            require(root.has("ref_hor")) { "champ 'ref_hor' manquant" }
            require(root.has("villes")) { "champ 'villes' manquant" }
            // Force le parsing complet pour detecter les erreurs de structure profonde
            parseRoot(root)
        }

        private fun build(context: Context): SalatDataRepository {
            val prefs = PrefsManager(context)
            val text: String = if (prefs.useImportedData && importFile().exists()) {
                try {
                    importFile().readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    // fichier illisible : fallback silencieux sur l'asset d'origine
                    context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            } else {
                context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }

            val root = JSONObject(text)
            return parseRoot(root)
        }

        private fun parseRoot(root: JSONObject): SalatDataRepository {
            // horaires
            val horArr: JSONArray = root.getJSONArray("horaires")
            val horMap = HashMap<LocalDate, HoraireEntry>(horArr.length())
            var minDate: LocalDate? = null
            var maxDate: LocalDate? = null
            for (i in 0 until horArr.length()) {
                val o = horArr.getJSONObject(i)
                val date = LocalDate.parse(o.getString("g"), ISO)
                val entry = HoraireEntry(
                    hy = o.getInt("hy"),
                    hm = o.getInt("hm"),
                    hd = o.getInt("hd"),
                    g = o.getString("g"),
                    times = DayTimes(
                        fa = o.optStringOrNull("fa"),
                        ch = o.optStringOrNull("ch"),
                        do_ = o.optStringOrNull("do"),
                        as_ = o.optStringOrNull("as"),
                        ma = o.optStringOrNull("ma"),
                        is_ = o.optStringOrNull("is")
                    )
                )
                horMap[date] = entry
                if (minDate == null || date.isBefore(minDate)) minDate = date
                if (maxDate == null || date.isAfter(maxDate)) maxDate = date
            }

            // ref_hor
            val refArr: JSONArray = root.getJSONArray("ref_hor")
            val refMap = HashMap<Int, RefHorEntry>(refArr.length())
            for (i in 0 until refArr.length()) {
                val o = refArr.getJSONObject(i)
                val doy = o.getInt("doy")
                refMap[doy] = RefHorEntry(
                    doy = doy,
                    times = DayTimes(
                        fa = o.optStringOrNull("fa"),
                        ch = o.optStringOrNull("ch"),
                        do_ = o.optStringOrNull("do"),
                        as_ = o.optStringOrNull("as"),
                        ma = o.optStringOrNull("ma"),
                        is_ = o.optStringOrNull("is")
                    )
                )
            }

            // villes
            val vilArr: JSONArray = root.getJSONArray("villes")
            val villesList = ArrayList<Ville>(vilArr.length())
            for (i in 0 until vilArr.length()) {
                val o = vilArr.getJSONObject(i)
                val deltasArr = o.getJSONArray("deltas")
                val deltas = ArrayList<VilleDelta>(deltasArr.length())
                for (j in 0 until deltasArr.length()) {
                    val d = deltasArr.getJSONObject(j)
                    deltas.add(
                        VilleDelta(
                            hy = d.getInt("hy"),
                            hm = d.getInt("hm"),
                            du = d.getInt("du"),
                            au = d.getInt("au"),
                            dFa = d.getInt("d_fa"),
                            dCh = d.getInt("d_ch"),
                            dDo = d.getInt("d_do"),
                            dAs = d.getInt("d_as"),
                            dMa = d.getInt("d_ma"),
                            dIs = d.getInt("d_is")
                        )
                    )
                }
                villesList.add(Ville(id = o.getInt("id"), nom = o.getString("nom"), deltas = deltas))
            }

            return SalatDataRepository(
                horairesByDate = horMap,
                refHorByDoy = refMap,
                villes = villesList.sortedBy { it.nom },
                horaireRange = Pair(minDate!!, maxDate!!)
            )
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (this.isNull(key) || !this.has(key)) null else this.optString(key)
    }
}

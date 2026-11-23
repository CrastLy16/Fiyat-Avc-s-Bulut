package com.fiyatavcisi.bulut.data

import android.content.Context
import com.fiyatavcisi.bulut.model.CloudCategory
import com.fiyatavcisi.bulut.model.CloudDeal
import com.fiyatavcisi.bulut.model.CloudOffer
import com.fiyatavcisi.bulut.model.CloudProduct
import com.fiyatavcisi.bulut.model.CloudSettings
import com.fiyatavcisi.bulut.model.CloudSnapshot
import com.fiyatavcisi.bulut.model.MatchStatus
import com.fiyatavcisi.bulut.model.StoreSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class CloudRepository(context: Context) {

    private val prefs = context.getSharedPreferences("fiyat_avcisi_bulut_prefs", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "cloud_snapshot_cache.json")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun loadSettings(): CloudSettings {
        val rawSites = prefs.getStringSet("enabled_sites", null)
        val enabled = rawSites?.map { StoreSite.fromCode(it) }?.toSet()
            ?: StoreSite.entries.filter { it != StoreSite.OTHER }.toSet()
        return CloudSettings(
            githubUsername = prefs.getString("gh_user", "").orEmpty(),
            githubRepo = prefs.getString("gh_repo", "").orEmpty(),
            branch = prefs.getString("gh_branch", "main").orEmpty(),
            notifyTrackedProducts = prefs.getBoolean("notify_products", true),
            notifyAnyCategoryDeals = prefs.getBoolean("notify_categories", true),
            minDropPercent = prefs.getInt("min_drop_pct", 15),
            syncIntervalMinutes = prefs.getLong("sync_interval", 30L),
            backgroundSyncEnabled = prefs.getBoolean("bg_sync", true),
            enabledSites = enabled
        )
    }

    fun saveSettings(s: CloudSettings) {
        prefs.edit()
            .putString("gh_user", s.githubUsername.trim())
            .putString("gh_repo", s.githubRepo.trim())
            .putString("gh_branch", s.branch.trim().ifBlank { "main" })
            .putBoolean("notify_products", s.notifyTrackedProducts)
            .putBoolean("notify_categories", s.notifyAnyCategoryDeals)
            .putInt("min_drop_pct", s.minDropPercent)
            .putLong("sync_interval", s.syncIntervalMinutes)
            .putBoolean("bg_sync", s.backgroundSyncEnabled)
            .putStringSet("enabled_sites", s.enabledSites.map { it.name }.toSet())
            .apply()
    }

    fun getNotifiedDealKeys(): Set<String> = prefs.getStringSet("notified_keys", emptySet()) ?: emptySet()

    fun markDealsNotified(keys: Set<String>) {
        val merged = (getNotifiedDealKeys() + keys).toList().takeLast(200).toSet()
        prefs.edit().putStringSet("notified_keys", merged).apply()
    }

    fun loadCachedSnapshot(): CloudSnapshot {
        if (!cacheFile.exists()) return CloudSnapshot()
        return runCatching { parseSnapshot(cacheFile.readText(Charsets.UTF_8)) }.getOrDefault(CloudSnapshot())
    }

    suspend fun fetchLatestSnapshot(settings: CloudSettings): Result<CloudSnapshot> = withContext(Dispatchers.IO) {
        val url = settings.snapshotRawUrl
        if (url.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Ayarlar sekmesinden GitHub kullanıcı adı ve depo adını girin."))
        }
        try {
            val request = Request.Builder()
                .url("$url?t=${System.currentTimeMillis()}")
                .header("User-Agent", "FiyatAvcisiBulut/2.0")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Bulut verisi alınamadı (HTTP ${resp.code}). Depo Public olmalı ve ilk tarama tamamlanmalı.")
                    )
                }
                val raw = resp.body?.string().orEmpty()
                val snapshot = parseSnapshot(raw)
                cacheFile.writeText(raw, Charsets.UTF_8)
                Result.success(snapshot)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSnapshot(raw: String): CloudSnapshot {
        val root = JSONObject(raw)
        val updatedAt = root.optLong("updatedAt", 0L)

        val products = mutableListOf<CloudProduct>()
        val prodArr = root.optJSONArray("products") ?: JSONArray()
        for (i in 0 until prodArr.length()) {
            val p = prodArr.optJSONObject(i) ?: continue
            val offers = mutableListOf<CloudOffer>()
            val offArr = p.optJSONArray("offers") ?: JSONArray()
            for (j in 0 until offArr.length()) {
                val o = offArr.optJSONObject(j) ?: continue
                offers.add(
                    CloudOffer(
                        site = StoreSite.fromCode(o.optString("site")),
                        url = o.optString("url"),
                        title = o.optString("title"),
                        price = if (!o.isNull("price")) o.optDouble("price") else null,
                        previousPrice = if (!o.isNull("previousPrice")) o.optDouble("previousPrice") else null,
                        dropPercent = o.optDouble("dropPercent", 0.0),
                        status = o.optString("status"),
                        matchStatus = MatchStatus.fromCode(o.optString("matchStatus")),
                        matchScore = o.optInt("matchScore", 0)
                    )
                )
            }
            products.add(
                CloudProduct(
                    id = p.optString("id", "p_$i"),
                    name = p.optString("name", "İsimsiz Ürün"),
                    targetDropPercent = p.optInt("targetDropPercent", 15),
                    offers = offers
                )
            )
        }

        val categories = mutableListOf<CloudCategory>()
        val catArr = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until catArr.length()) {
            val c = catArr.optJSONObject(i) ?: continue
            categories.add(
                CloudCategory(
                    id = c.optString("id", "c_$i"),
                    name = c.optString("name", "Kategori"),
                    site = StoreSite.fromCode(c.optString("site")),
                    url = c.optString("url"),
                    minDropPercent = c.optInt("minDropPercent", 20),
                    scannedCount = c.optInt("scannedCount", 0),
                    status = c.optString("status")
                )
            )
        }

        val deals = mutableListOf<CloudDeal>()
        val dealArr = root.optJSONArray("bigDeals") ?: JSONArray()
        for (i in 0 until dealArr.length()) {
            val d = dealArr.optJSONObject(i) ?: continue
            val url = d.optString("url")
            val newPrice = d.optDouble("newPrice", 0.0)
            deals.add(
                CloudDeal(
                    id = "${url}_${newPrice}",
                    title = d.optString("title", "Büyük İndirim"),
                    site = StoreSite.fromCode(d.optString("site")),
                    url = url,
                    oldPrice = d.optDouble("oldPrice", 0.0),
                    newPrice = newPrice,
                    dropPercent = d.optDouble("dropPercent", 0.0),
                    comparisonSummary = d.optString("comparisonSummary"),
                    isFromCategory = d.optBoolean("isFromCategory", false)
                )
            )
        }

        return CloudSnapshot(
            updatedAt = updatedAt,
            products = products,
            categories = categories,
            bigDeals = deals
        )
    }
}

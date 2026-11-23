package com.fiyatavcisi.bulut.model

enum class StoreSite(val displayName: String) {
    AMAZON("Amazon TR"),
    HEPSIBURADA("Hepsiburada"),
    N11("N11"),
    TRENDYOL("Trendyol"),
    VATAN("Vatan"),
    TEKNOSA("Teknosa"),
    MEDIAMARKT("MediaMarkt"),
    OTHER("Diğer");

    companion object {
        fun fromCode(code: String): StoreSite {
            return runCatching { valueOf(code.uppercase()) }.getOrDefault(OTHER)
        }
    }
}

enum class MatchStatus(val label: String) {
    VERIFIED_BARCODE("Barkod/GTIN Doğrulandı"),
    VERIFIED_MODEL("Model Kodu Eşleşti"),
    VERIFIED_HIGH("Aynı Ürün Doğrulandı"),
    NEEDS_REVIEW("Farklı Ürün Olabilir"),
    REFERENCE("Referans Ürün"),
    UNCHECKED("Doğrulanmadı");

    companion object {
        fun fromCode(code: String): MatchStatus {
            return runCatching { valueOf(code.uppercase()) }.getOrDefault(UNCHECKED)
        }
    }
}

data class CloudOffer(
    val site: StoreSite,
    val url: String,
    val title: String,
    val price: Double?,
    val previousPrice: Double?,
    val dropPercent: Double,
    val status: String,
    val matchStatus: MatchStatus,
    val matchScore: Int
)

data class CloudProduct(
    val id: String,
    val name: String,
    val targetDropPercent: Int,
    val offers: List<CloudOffer>
)

data class CloudCategory(
    val id: String,
    val name: String,
    val site: StoreSite,
    val url: String,
    val minDropPercent: Int,
    val scannedCount: Int,
    val status: String
)

data class CloudDeal(
    val id: String,
    val title: String,
    val site: StoreSite,
    val url: String,
    val oldPrice: Double,
    val newPrice: Double,
    val dropPercent: Double,
    val comparisonSummary: String,
    val isFromCategory: Boolean
)

data class CloudSnapshot(
    val updatedAt: Long = 0L,
    val products: List<CloudProduct> = emptyList(),
    val categories: List<CloudCategory> = emptyList(),
    val bigDeals: List<CloudDeal> = emptyList()
)

data class CloudSettings(
    val githubUsername: String = "",
    val githubRepo: String = "",
    val branch: String = "main",
    val notifyTrackedProducts: Boolean = true,
    val notifyAnyCategoryDeals: Boolean = true,
    val minDropPercent: Int = 15,
    val syncIntervalMinutes: Long = 30L,
    val backgroundSyncEnabled: Boolean = true,
    val enabledSites: Set<StoreSite> = StoreSite.entries.filter { it != StoreSite.OTHER }.toSet()
) {
    val snapshotRawUrl: String
        get() = if (githubUsername.isNotBlank() && githubRepo.isNotBlank()) {
            "https://raw.githubusercontent.com/${githubUsername.trim()}/${githubRepo.trim()}/${branch.trim().ifBlank { "main" }}/cloud_monitor/deals_snapshot.json"
        } else {
            ""
        }

    val watchlistEditUrl: String
        get() = if (githubUsername.isNotBlank() && githubRepo.isNotBlank()) {
            "https://github.com/${githubUsername.trim()}/${githubRepo.trim()}/edit/${branch.trim().ifBlank { "main" }}/cloud_monitor/watchlist.json"
        } else {
            "https://github.com"
        }
}

package com.fiyatavcisi.bulut.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fiyatavcisi.bulut.model.CloudDeal
import com.fiyatavcisi.bulut.model.CloudOffer
import com.fiyatavcisi.bulut.model.CloudProduct
import com.fiyatavcisi.bulut.model.MatchStatus
import com.fiyatavcisi.bulut.model.StoreSite
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAppScreen(viewModel: CloudViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(if (state.settings.snapshotRawUrl.isBlank()) 2 else 0) }
    val tl = remember { NumberFormat.getCurrencyInstance(Locale("tr", "TR")) }
    val dateFmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale("tr", "TR")) }

    val openUrl: (String) -> Unit = { url ->
        if (url.startsWith("http")) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Fiyat Avcısı Bulut", fontWeight = FontWeight.Bold)
                        val sub = when {
                            state.statusText.isNotBlank() -> state.statusText
                            state.snapshot.updatedAt > 0L -> "Bulut taraması: ${dateFmt.format(Date(state.snapshot.updatedAt))}"
                            else -> "0 pil tüketimli GitHub Bulut Takip"
                        }
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = { openUrl(state.settings.watchlistEditUrl) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Listeyi GitHub'da Düzenle")
                    }
                    IconButton(onClick = { viewModel.syncNow() }, enabled = !state.isSyncing) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Buluttan Yenile")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.LocalOffer, contentDescription = null) },
                    label = { Text("Ürünler (${state.snapshot.products.size})") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    label = { Text("Fırsatlar (${state.snapshot.bigDeals.size})") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Bulut & Ayarlar") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> CloudProductsTab(
                    products = state.snapshot.products,
                    enabledSites = state.settings.enabledSites,
                    tl = tl,
                    onOpenUrl = openUrl,
                    onEditList = { openUrl(state.settings.watchlistEditUrl) }
                )
                1 -> CloudDealsTab(
                    deals = state.snapshot.bigDeals.filter {
                        (it.site == StoreSite.OTHER || it.site in state.settings.enabledSites) &&
                            it.dropPercent >= state.settings.minDropPercent &&
                            ((it.isFromCategory && state.settings.notifyAnyCategoryDeals) ||
                                (!it.isFromCategory && state.settings.notifyTrackedProducts))
                    },
                    tl = tl,
                    onOpenUrl = openUrl
                )
                2 -> CloudSettingsTab(state = state, viewModel = viewModel, onEditList = { openUrl(state.settings.watchlistEditUrl) })
            }
        }
    }
}

@Composable
private fun CloudProductsTab(
    products: List<CloudProduct>,
    enabledSites: Set<StoreSite>,
    tl: NumberFormat,
    onOpenUrl: (String) -> Unit,
    onEditList: () -> Unit
) {
    if (products.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Bulut & Ayarlar sekmesinden GitHub deponu bağla. Ardından ürün ve kategori linklerini cloud_monitor/watchlist.json dosyasına ekle; bulut 30 dakikada bir tarayıp buraya düşürür.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onEditList) { Text("Watchlist Dosyasını GitHub'da Aç") }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(products, key = { it.id }) { prod ->
            val visibleOffers = prod.offers.filter { it.site == StoreSite.OTHER || it.site in enabledSites }
            val cheapest = visibleOffers.mapNotNull { it.price }.minOrNull()

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(prod.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Bildirim eşiği: %${prod.targetDropPercent}+ düşüş",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    visibleOffers.sortedBy { it.price ?: Double.MAX_VALUE }.forEach { offer ->
                        CloudOfferRow(
                            offer = offer,
                            isCheapest = cheapest != null && offer.price == cheapest,
                            tl = tl,
                            onOpen = { onOpenUrl(offer.url) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudOfferRow(
    offer: CloudOffer,
    isCheapest: Boolean,
    tl: NumberFormat,
    onOpen: () -> Unit
) {
    val bg = if (isCheapest) Color(0xFF064E3B).copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(offer.site.displayName, fontWeight = FontWeight.SemiBold)
                if (isCheapest) {
                    Text("EN UCUZ", style = MaterialTheme.typography.labelSmall, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                }
            }
            if (offer.title.isNotBlank()) {
                Text(
                    text = offer.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                val color = when (offer.matchStatus) {
                    MatchStatus.NEEDS_REVIEW -> Color(0xFFF59E0B)
                    MatchStatus.UNCHECKED -> Color(0xFF94A3B8)
                    else -> Color(0xFF34D399)
                }
                Icon(
                    imageVector = if (offer.matchStatus == MatchStatus.NEEDS_REVIEW) Icons.Default.Warning else Icons.Default.Verified,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${offer.matchStatus.label}${if (offer.matchScore > 0) " (%${offer.matchScore})" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = offer.price?.let { tl.format(it) } ?: offer.status,
                fontWeight = FontWeight.Bold,
                color = if (offer.price != null) MaterialTheme.colorScheme.onSurface else Color(0xFFFCA5A5)
            )
            if (offer.dropPercent > 0.1) {
                Text(
                    text = "-%${String.format(Locale("tr", "TR"), "%.1f", offer.dropPercent)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF34D399),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        IconButton(onClick = onOpen) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = "Ürünü Aç")
        }
    }
}

@Composable
private fun CloudDealsTab(
    deals: List<CloudDeal>,
    tl: NumberFormat,
    onOpenUrl: (String) -> Unit
) {
    if (deals.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Seçili indirim eşiğini aşan bir fırsat henüz yakalanmadı. Bulut tarayıcısı her 30 dakikada bir kontrol edip indirim bulduğunda bildirim gönderecektir.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(deals, key = { it.id }) { deal ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "-%${String.format(Locale("tr", "TR"), "%.1f", deal.dropPercent)} • ${deal.site.displayName}",
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Bold
                    )
                    Text(deal.title, fontWeight = FontWeight.SemiBold)
                    Text("${tl.format(deal.oldPrice)} → ${tl.format(deal.newPrice)}")
                    if (deal.comparisonSummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = deal.comparisonSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onOpenUrl(deal.url) }) { Text("Ürüne Git") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudSettingsTab(
    state: CloudUiState,
    viewModel: CloudViewModel,
    onEditList: () -> Unit
) {
    val s = state.settings
    var ghUser by remember(s.githubUsername) { mutableStateOf(s.githubUsername) }
    var ghRepo by remember(s.githubRepo) { mutableStateOf(s.githubRepo) }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GitHub Bulut Bağlantısı (0 Telefon Pili)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = ghUser,
                        onValueChange = { ghUser = it },
                        label = { Text("GitHub Kullanıcı Adın") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ghRepo,
                        onValueChange = { ghRepo = it },
                        label = { Text("Depo Adı (örn. FiyatAvcisiTR)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            viewModel.updateSettings { it.copy(githubUsername = ghUser, githubRepo = ghRepo) }
                            viewModel.syncNow()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Buluta Bağlan ve Senkronize Et") }

                    OutlinedButton(onClick = onEditList, modifier = Modifier.fillMaxWidth()) {
                        Text("Takip Listesini (watchlist.json) GitHub'da Düzenle")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bildirim Tercihleri", fontWeight = FontWeight.Bold)
                    SettingSwitch("Seçili ürün fiyat düşüşleri", s.notifyTrackedProducts) { v ->
                        viewModel.updateSettings { it.copy(notifyTrackedProducts = v) }
                    }
                    SettingSwitch("Kategori / Herhangi bir ürün büyük indirimleri", s.notifyAnyCategoryDeals) { v ->
                        viewModel.updateSettings { it.copy(notifyAnyCategoryDeals = v) }
                    }
                    SettingSwitch("Arka plan bildirim senkronizasyonu", s.backgroundSyncEnabled) { v ->
                        viewModel.updateSettings { it.copy(backgroundSyncEnabled = v) }
                    }
                    Text("Minimum indirim bildirimi eşiği: %${s.minDropPercent}+", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = s.minDropPercent.toFloat(),
                        onValueChange = { v -> viewModel.updateSettings { it.copy(minDropPercent = v.toInt()) } },
                        valueRange = 5f..70f
                    )
                    Text("Bulut kontrol sıklığı (telefon tarafı):", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15L to "15 dk", 30L to "30 dk", 60L to "1 saat").forEach { (mins, label) ->
                            FilterChip(
                                selected = s.syncIntervalMinutes == mins,
                                onClick = { viewModel.updateSettings { it.copy(syncIntervalMinutes = mins) } },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aktif Siteler (7 Site)", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StoreSite.entries.filter { it != StoreSite.OTHER }.forEach { site ->
                            FilterChip(
                                selected = site in s.enabledSites,
                                onClick = {
                                    viewModel.updateSettings { st ->
                                        val next = st.enabledSites.toMutableSet()
                                        if (!next.add(site)) next.remove(site)
                                        st.copy(enabledSites = next)
                                    }
                                },
                                label = { Text(site.displayName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

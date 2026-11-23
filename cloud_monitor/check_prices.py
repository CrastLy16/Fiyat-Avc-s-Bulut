#!/usr/bin/env python3
"""
Bulut Fiyat Tarayicisi (GitHub Actions - 0 Telefon Pili)
- 7 site: Amazon TR, Hepsiburada, N11, Trendyol, Vatan, Teknosa, MediaMarkt
- Once hizli HTTP, gerekirse gercek Chromium (Playwright) ile JS render
- GTIN/EAN barkod, model kodu, teknik birim ve Turkce baslik benzerligi ile ayni urun dogrulamasi
- Hem secili urunlerde hem kategori/firsat sayfalarinda herhangi bir urunde %X+ indirim yakalama
"""

import json
import re
import time
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent
WATCHLIST_PATH = ROOT / "watchlist.json"
SNAPSHOT_PATH = ROOT / "deals_snapshot.json"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "tr-TR,tr;q=0.9,en-US;q=0.8",
}

MODEL_RE = re.compile(r"\b(?=[A-Z0-9-]{5,18}\b)(?=.*[A-Z])(?=.*\d)[A-Z0-9-]+\b")
SPEC_RE = re.compile(r"\b\d+\s?(?:gb|tb|mb|mah|w|watt|lt|l|ml|kg|gr|g|inc|inch|hz|cm|mm)\b")
PRICE_RE = re.compile(r"(\d{1,3}(?:\.\d{3})*(?:,\d{2})?|\d+(?:\.\d{2})?)")

STOP_WORDS = {
    "ve", "ile", "icin", "yeni", "orijinal", "garantili", "turkiye", "distribitor",
    "resmi", "satis", "fiyat", "en", "ucuz", "kargo", "bedava", "hizli", "teslimat",
    "siyah", "beyaz", "mavi", "gri", "kirmizi", "yesil", "mor", "gumus", "altin",
    "amazon", "hepsiburada", "trendyol", "n11", "vatan", "teknosa", "mediamarkt",
}

SITE_SELECTORS = {
    "AMAZON": ["span.a-price-whole", "#priceblock_ourprice", ".a-offscreen"],
    "HEPSIBURADA": ["[data-test-id='price-current-price']", "#offering-price"],
    "TRENDYOL": ["span.prc-dsc", "span.prc-slg"],
    "N11": [".newPrice ins", "input#productDisplayPrice"],
    "VATAN": [".product-list__price", ".product-detail-price"],
    "TEKNOSA": [".prc-last", ".pdp-price"],
    "MEDIAMARKT": ["[data-test='branded-price-whole-value']", ".price"],
}


def norm_tr(text: str) -> str:
    return (
        text.lower()
        .replace("ç", "c")
        .replace("ğ", "g")
        .replace("ı", "i")
        .replace("ö", "o")
        .replace("ş", "s")
        .replace("ü", "u")
    )


def detect_site(url: str) -> str:
    host = (urlparse(url).hostname or "").lower()
    for key, label in [
        ("amazon", "AMAZON"),
        ("hepsiburada", "HEPSIBURADA"),
        ("n11", "N11"),
        ("trendyol", "TRENDYOL"),
        ("vatanbilgisayar", "VATAN"),
        ("teknosa", "TEKNOSA"),
        ("mediamarkt", "MEDIAMARKT"),
    ]:
        if key in host:
            return label
    return "OTHER"


def parse_price(raw: Any) -> float | None:
    if raw is None:
        return None
    if isinstance(raw, (int, float)):
        return float(raw) if raw > 0 else None
    m = PRICE_RE.search(str(raw).replace(" ", ""))
    if not m:
        return None
    s = m.group(1)
    if "." in s and "," in s:
        s = s.replace(".", "").replace(",", ".") if s.rfind(",") > s.rfind(".") else s.replace(",", "")
    elif "," in s:
        s = s.replace(",", ".") if len(s.split(",")[-1]) == 2 else s.replace(",", "")
    elif "." in s:
        s = s.replace(".", "") if len(s.split(".")[-1]) == 3 else s
    try:
        val = float(s)
        return val if val > 0 else None
    except ValueError:
        return None


def extract_model_code(title: str, sku: str = "") -> str:
    clean_sku = sku.strip().upper()
    if 5 <= len(clean_sku) <= 24 and any(ch.isdigit() for ch in clean_sku):
        return clean_sku
    for match in MODEL_RE.findall(title.upper()):
        if not match.endswith(("GB", "TB", "HZ", "W")):
            return match
    return ""


def verify_same_product(ref: dict[str, Any], cand: dict[str, Any]) -> tuple[str, int]:
    if not (cand.get("title") or cand.get("gtin") or cand.get("modelCode")):
        return "UNCHECKED", 0

    ref_gtin = re.sub(r"\D", "", str(ref.get("gtin") or ""))
    cand_gtin = re.sub(r"\D", "", str(cand.get("gtin") or ""))
    if len(ref_gtin) >= 8 and len(cand_gtin) >= 8:
        return ("VERIFIED_BARCODE", 100) if ref_gtin == cand_gtin else ("NEEDS_REVIEW", 20)

    ref_model = (ref.get("modelCode") or extract_model_code(ref.get("title", ""))).upper()
    cand_model = (cand.get("modelCode") or extract_model_code(cand.get("title", ""))).upper()
    if len(ref_model) >= 5 and len(cand_model) >= 5:
        if ref_model == cand_model or ref_model in cand_model or cand_model in ref_model:
            return "VERIFIED_MODEL", 95

    ref_specs = {x.replace(" ", "") for x in SPEC_RE.findall(norm_tr(ref.get("title", "")))}
    cand_specs = {x.replace(" ", "") for x in SPEC_RE.findall(norm_tr(cand.get("title", "")))}
    if ref_specs and cand_specs and not (ref_specs & cand_specs):
        return "NEEDS_REVIEW", 35

    ref_toks = {t for t in re.split(r"[^a-z0-9]+", norm_tr(ref.get("title", ""))) if len(t) >= 2 and t not in STOP_WORDS}
    cand_toks = {t for t in re.split(r"[^a-z0-9]+", norm_tr(cand.get("title", ""))) if len(t) >= 2 and t not in STOP_WORDS}
    if not ref_toks or not cand_toks:
        return "UNCHECKED", 0

    overlap = len(ref_toks & cand_toks)
    jaccard = overlap / len(ref_toks | cand_toks)
    containment = overlap / min(len(ref_toks), len(cand_toks))
    score = int((jaccard * 0.5 + containment * 0.5) * 100)

    if ref.get("brand") and cand.get("brand"):
        if norm_tr(ref["brand"]) == norm_tr(cand["brand"]):
            score = min(98, score + 12)
        else:
            score = max(10, score - 25)

    return ("VERIFIED_HIGH" if score >= 62 else "NEEDS_REVIEW"), max(0, min(100, score))


def extract_from_html(html: str, site: str) -> dict[str, Any]:
    soup = BeautifulSoup(html, "html.parser")
    title = soup.title.string.strip() if soup.title and soup.title.string else ""
    price = None
    brand = ""
    gtin = ""
    sku = ""

    for script in soup.find_all("script", type="application/ld+json"):
        try:
            data = json.loads(script.string or "")
        except Exception:
            continue
        objs = data if isinstance(data, list) else [data]
        expanded = []
        for obj in objs:
            if isinstance(obj, dict):
                expanded.append(obj)
                if isinstance(obj.get("@graph"), list):
                    expanded.extend([g for g in obj["@graph"] if isinstance(g, dict)])
        for obj in expanded:
            if "Product" not in str(obj.get("@type", "")):
                continue
            title = obj.get("name") or title
            b = obj.get("brand")
            brand = b.get("name", "") if isinstance(b, dict) else str(b or "")
            gtin = str(obj.get("gtin13") or obj.get("gtin") or obj.get("ean") or "")
            sku = str(obj.get("sku") or obj.get("mpn") or "")
            offers = obj.get("offers")
            if isinstance(offers, list) and offers:
                offers = offers[0]
            if isinstance(offers, dict):
                price = parse_price(offers.get("price") or offers.get("lowPrice"))
            if price:
                break
        if price:
            break

    if price is None:
        for attr_key, attr_val in [("property", "product:price:amount"), ("property", "og:price:amount"), ("itemprop", "price")]:
            meta = soup.find("meta", attrs={attr_key: attr_val})
            if meta and meta.get("content"):
                price = parse_price(meta["content"])
                if price:
                    break

    if price is None:
        for sel in SITE_SELECTORS.get(site, ["[itemprop='price']", ".price"]):
            el = soup.select_one(sel)
            if el:
                raw = el.get("value") or el.get_text(" ", strip=True)
                price = parse_price(raw)
                if price:
                    break

    return {
        "title": title.strip(),
        "price": price,
        "brand": brand.strip(),
        "gtin": gtin.strip(),
        "modelCode": extract_model_code(title, sku),
    }


def extract_category_items(html: str) -> list[dict[str, Any]]:
    soup = BeautifulSoup(html, "html.parser")
    items = []
    seen = set()
    for script in soup.find_all("script", type="application/ld+json"):
        try:
            data = json.loads(script.string or "")
        except Exception:
            continue
        objs = data if isinstance(data, list) else [data]
        for obj in objs:
            if not isinstance(obj, dict):
                continue
            elements = obj.get("itemListElement")
            if not isinstance(elements, list):
                continue
            for el in elements:
                if not isinstance(el, dict):
                    continue
                item = el.get("item") if isinstance(el.get("item"), dict) else el
                name = str(item.get("name") or "").strip()
                item_url = str(item.get("url") or "").strip()
                offers = item.get("offers")
                if isinstance(offers, list) and offers:
                    offers = offers[0]
                price = parse_price(offers.get("price") or offers.get("lowPrice")) if isinstance(offers, dict) else None
                if name and item_url.startswith("http") and price and item_url not in seen:
                    seen.add(item_url)
                    items.append({"title": name, "url": item_url, "price": price})
    return items[:80]


def fetch_html_smart(url: str, pw_browser: Any = None) -> tuple[str | None, str]:
    # 1) Hizli requests denemesi
    try:
        resp = requests.get(url, headers=HEADERS, timeout=15)
        if resp.status_code == 200 and len(resp.text) > 1500:
            return resp.text, "HTTP"
    except Exception:
        pass

    # 2) Gercek Chromium (Playwright) yedegi
    if pw_browser is not None:
        try:
            page = pw_browser.new_page(user_agent=HEADERS["User-Agent"], locale="tr-TR")
            page.goto(url, wait_until="domcontentloaded", timeout=25000)
            page.wait_for_timeout(2500)
            html = page.content()
            page.close()
            if html and len(html) > 1500:
                return html, "Chromium"
        except Exception as exc:
            return None, f"Chromium hatasi: {exc}"

    return None, "Erisilemedi / Bot korumasi"


def main() -> None:
    if not WATCHLIST_PATH.exists():
        print("watchlist.json bulunamadi.")
        return

    watchlist = json.loads(WATCHLIST_PATH.read_text(encoding="utf-8"))
    enabled_sites = set(watchlist.get("enabledSites", []))
    global_min_drop = float(watchlist.get("globalMinDropPercent", 20))

    # Onceki anlik goruntuden eski fiyatlari yukle
    prev_offer_prices: dict[str, float] = {}
    prev_cat_prices: dict[str, dict[str, float]] = {}
    if SNAPSHOT_PATH.exists():
        try:
            old = json.loads(SNAPSHOT_PATH.read_text(encoding="utf-8"))
            for p in old.get("products", []):
                for o in p.get("offers", []):
                    if o.get("url") and o.get("price"):
                        prev_offer_prices[o["url"]] = float(o["price"])
            for c in old.get("categories", []):
                if c.get("id") and isinstance(c.get("knownPrices"), dict):
                    prev_cat_prices[c["id"]] = {k: float(v) for k, v in c["knownPrices"].items() if v}
        except Exception:
            pass

    pw_ctx = None
    browser = None
    try:
        from playwright.sync_api import sync_playwright
        pw_ctx = sync_playwright().start()
        browser = pw_ctx.chromium.launch(headless=True)
    except Exception:
        browser = None

    now_ms = int(time.time() * 1000)
    out_products = []
    out_categories = []
    big_deals = []

    try:
        # 1) Takip edilen urun gruplari
        for prod in watchlist.get("products", []):
            threshold = float(prod.get("targetDropPercent", global_min_drop))
            raw_offers = []
            for offer in prod.get("offers", []):
                url = str(offer.get("url") or "").strip()
                if not url:
                    continue
                site = offer.get("site") or detect_site(url)
                if enabled_sites and site != "OTHER" and site not in enabled_sites:
                    continue

                html, mode = fetch_html_smart(url, browser)
                meta = extract_from_html(html, site) if html else {"title": "", "price": None, "brand": "", "gtin": "", "modelCode": ""}
                curr_price = meta.get("price")
                prev_price = prev_offer_prices.get(url)
                drop_pct = 0.0
                if prev_price and curr_price and curr_price < prev_price:
                    drop_pct = round(((prev_price - curr_price) / prev_price) * 100.0, 1)

                raw_offers.append({
                    "site": site,
                    "url": url,
                    "title": meta.get("title", ""),
                    "brand": meta.get("brand", ""),
                    "gtin": meta.get("gtin", ""),
                    "modelCode": meta.get("modelCode", ""),
                    "price": curr_price,
                    "previousPrice": prev_price,
                    "dropPercent": drop_pct,
                    "status": f"Güncel ({mode})" if curr_price else f"Okunamadı ({mode})",
                })

            ref = next((o for o in raw_offers if o.get("title")), None)
            verified_offers = []
            for idx, o in enumerate(raw_offers):
                if idx == 0 and ref is o:
                    m_status, m_score = "REFERENCE", 100
                elif ref is not None:
                    m_status, m_score = verify_same_product(ref, o)
                else:
                    m_status, m_score = "UNCHECKED", 0
                o["matchStatus"] = m_status
                o["matchScore"] = m_score
                verified_offers.append(o)

            priced = sorted([o for o in verified_offers if o.get("price")], key=lambda x: x["price"])
            comp_summary = "\n".join(
                f"• {o['site']}: {o['price']:,.2f} TL" for o in priced
            )

            for o in verified_offers:
                if o.get("price") and o.get("previousPrice") and o["dropPercent"] >= threshold:
                    big_deals.append({
                        "title": prod.get("name") or o.get("title") or o["url"],
                        "site": o["site"],
                        "url": o["url"],
                        "oldPrice": o["previousPrice"],
                        "newPrice": o["price"],
                        "dropPercent": o["dropPercent"],
                        "comparisonSummary": comp_summary,
                        "isFromCategory": False,
                    })

            out_products.append({
                "id": prod.get("id", ""),
                "name": prod.get("name", ""),
                "targetDropPercent": int(threshold),
                "offers": verified_offers,
            })

        # 2) Kategori / firsat sayfalari (herhangi bir urunde buyuk indirim)
        for cat in watchlist.get("categories", []):
            cat_id = cat.get("id", "")
            url = str(cat.get("url") or "").strip()
            site = cat.get("site") or detect_site(url)
            if not url or (enabled_sites and site != "OTHER" and site not in enabled_sites):
                continue

            html, mode = fetch_html_smart(url, browser)
            items = extract_category_items(html) if html else []
            known = dict(prev_cat_prices.get(cat_id, {}))
            threshold = float(cat.get("minDropPercent", global_min_drop))

            for it in items:
                u = it["url"]
                new_p = float(it["price"])
                old_p = known.get(u)
                if old_p and old_p > new_p:
                    drop_pct = round(((old_p - new_p) / old_p) * 100.0, 1)
                    if drop_pct >= threshold:
                        big_deals.append({
                            "title": it["title"],
                            "site": site,
                            "url": u,
                            "oldPrice": old_p,
                            "newPrice": new_p,
                            "dropPercent": drop_pct,
                            "comparisonSummary": f"Kategori: {cat.get('name', '')} ({site})",
                            "isFromCategory": True,
                        })
                known[u] = new_p

            out_categories.append({
                "id": cat_id,
                "name": cat.get("name", ""),
                "site": site,
                "url": url,
                "minDropPercent": int(threshold),
                "scannedCount": len(items),
                "status": f"{len(items)} ürün tarandı ({mode})" if html else f"Taranamadı ({mode})",
                "knownPrices": known,
            })
    finally:
        if browser is not None:
            browser.close()
        if pw_ctx is not None:
            pw_ctx.stop()

    snapshot = {
        "updatedAt": now_ms,
        "products": out_products,
        "categories": out_categories,
        "bigDeals": big_deals,
    }
    SNAPSHOT_PATH.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Bulut taramasi tamamlandi: {len(out_products)} urun, {len(out_categories)} kategori, {len(big_deals)} buyuk indirim.")


if __name__ == "__main__":
    main()

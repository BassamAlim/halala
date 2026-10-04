#!/usr/bin/env python3
"""
Builds Halala's bundled list of chains from OpenStreetMap's name-suggestion-index (NSI, BSD-3),
so more merchants are identified on the phone with no AI call.

    python3 scripts/nsi_merchants.py [nsi.json]

Without an argument it downloads the latest dist/nsi.json. Writes
app/src/main/resources/known_merchants_nsi.json in the definitions file's shape (KnownMerchants.parse).
Brands in Saudi Arabia are taken, and worldwide ones, less those named a single common English
word ("Garage", "Total"): a list match is trusted outright, so a wrong one costs more than none. The hand-kept list
in KnownMerchants beats this one.
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

URL = "https://cdn.jsdelivr.net/npm/name-suggestion-index@latest/dist/nsi.json"
OUT = Path(__file__).resolve().parent.parent / "app/src/main/resources/known_merchants_nsi.json"
WORLD = {"001", "142", "145"}  # the world, Asia, Western Asia

# An OSM tag (key=value) → BusinessType. Anything else (banks, ATMs, billboards) is left out.
TYPES = {
    "shop=supermarket": "SUPERMARKET", "shop=wholesale": "SUPERMARKET", "shop=greengrocer": "SUPERMARKET",
    "shop=convenience": "CONVENIENCE_STORE",
    "shop=bakery": "BAKERY", "shop=pastry": "BAKERY", "shop=confectionery": "BAKERY", "shop=chocolate": "BAKERY",
    "amenity=restaurant": "RESTAURANT",
    "amenity=fast_food": "FAST_FOOD",
    "amenity=cafe": "CAFE", "amenity=ice_cream": "CAFE", "shop=coffee": "CAFE",
    "amenity=fuel": "FUEL_STATION",
    "shop=car_repair": "CAR_SERVICE", "shop=tyres": "CAR_SERVICE", "shop=car_parts": "CAR_SERVICE",
    "shop=car": "CAR_SERVICE", "amenity=car_wash": "CAR_SERVICE",
    "amenity=parking": "PARKING",
    "amenity=car_rental": "CAR_RENTAL",
    "tourism=hotel": "HOTEL", "tourism=motel": "HOTEL", "tourism=apartment": "HOTEL", "tourism=guest_house": "HOTEL",
    "shop=travel_agency": "TRAVEL_AGENCY",
    "amenity=pharmacy": "PHARMACY", "shop=chemist": "PHARMACY",
    "amenity=clinic": "CLINIC", "amenity=dentist": "CLINIC", "amenity=doctors": "CLINIC", "amenity=hospital": "CLINIC",
    "healthcare=laboratory": "CLINIC",
    "shop=optician": "OPTICIAN",
    "leisure=fitness_centre": "GYM",
    "office=telecommunication": "TELECOM",
    "office=insurance": "INSURANCE",
    "amenity=school": "EDUCATION", "amenity=college": "EDUCATION", "amenity=university": "EDUCATION",
    "amenity=language_school": "EDUCATION", "amenity=prep_school": "EDUCATION", "amenity=training": "EDUCATION",
    "amenity=kindergarten": "EDUCATION",
    "shop=books": "BOOKSTORE", "shop=stationery": "BOOKSTORE",
    "shop=electronics": "ELECTRONICS", "shop=computer": "ELECTRONICS", "shop=mobile_phone": "ELECTRONICS",
    "shop=appliance": "ELECTRONICS",
    "shop=clothes": "CLOTHING", "shop=shoes": "CLOTHING", "shop=fashion_accessories": "CLOTHING", "shop=bag": "CLOTHING",
    "shop=cosmetics": "BEAUTY", "shop=perfumery": "BEAUTY", "shop=beauty": "BEAUTY",
    "shop=hairdresser": "SALON",
    "shop=jewelry": "JEWELRY", "shop=watches": "JEWELRY",
    "shop=gift": "GIFTS", "shop=florist": "GIFTS",
    "shop=sports": "SPORTS_GOODS", "shop=outdoor": "SPORTS_GOODS",
    "shop=toys": "TOYS", "shop=baby_goods": "TOYS",
    "shop=furniture": "HOME_FURNISHING", "shop=houseware": "HOME_FURNISHING", "shop=kitchen": "HOME_FURNISHING",
    "shop=interior_decoration": "HOME_FURNISHING", "shop=bed": "HOME_FURNISHING",
    "shop=hardware": "HARDWARE", "shop=doityourself": "HARDWARE",
    "shop=laundry": "LAUNDRY", "shop=dry_cleaning": "LAUNDRY",
    "shop=department_store": "DEPARTMENT_STORE", "shop=variety_store": "DEPARTMENT_STORE",
    "amenity=cinema": "ENTERTAINMENT", "tourism=theme_park": "ENTERTAINMENT",
    "leisure=trampoline_park": "ENTERTAINMENT", "leisure=amusement_arcade": "ENTERTAINMENT",
    "leisure=bowling_alley": "ENTERTAINMENT",
    "amenity=money_transfer": "MONEY_TRANSFER", "amenity=bureau_de_change": "MONEY_TRANSFER",
}

# Trades chains run on the side of their main one.
SIDELINES = {"FUEL_STATION", "CAR_SERVICE", "PHARMACY", "CAFE", "OPTICIAN", "PARKING", "CLINIC"}

# Words that name a kind of place, not a brand: on their own they'd claim every such shop.
GENERIC = {"محطة", "محطه", "نفط", "صيدلية", "صيدليه", "مكتبة", "مكتبه", "مطعم", "بقالة", "بقاله", "سوق", "أسواق",
           "اسواق", "مخبز", "كافيه", "مقهى", "فندق", "مستشفى", "عيادة", "مغسلة", "station", "pharmacy", "market"}

NAME_KEYS = ("name:en", "name", "name:ar")
# The brand's own names, when the entry is the brand itself rather than one of its lines ("IKEA Bistro").
BRAND_KEYS = ("brand:en", "brand", "brand:ar")


def load(argv):
    if len(argv) > 1:
        return json.load(open(argv[1], encoding="utf-8"))
    with urllib.request.urlopen(URL) as response:
        return json.load(response)


def english_words():
    try:
        return {w.strip().lower() for w in open("/usr/share/dict/words", encoding="utf-8")}
    except OSError:
        sys.exit("needs /usr/share/dict/words to leave out brands named a common word")


def in_saudi(item):
    where = item.get("locationSet", {})
    # Codes and region ids; a [lon, lat, radius] circle is too local to count.
    include = {x for x in where.get("include", []) if isinstance(x, str)}
    exclude = {x for x in where.get("exclude", []) if isinstance(x, str)}
    if "sa" in exclude:
        return None
    if "sa" in include:
        return "sa"
    return "world" if include & WORLD else None


def kept(spelling, words):
    letters = re.sub(r"[^\w]|[\d_]", "", spelling.lower())
    if len(letters) < 3 or spelling.strip().lower() in GENERIC:
        return False
    # A brand called a plain word ("Garage", "Total") would claim every shop of that word.
    return not (len(spelling.split()) == 1 and spelling.lower() in words)


def main():
    nsi = load(sys.argv)["nsi"]
    words = english_words()
    merchants = {}
    for path, group in sorted(nsi.items()):
        parts = path.split("/")
        if parts[0] != "brands" or len(parts) != 3:
            continue
        kind = TYPES.get(f"{parts[1]}={parts[2]}")
        if kind is None:
            continue
        for item in group["items"]:
            reach = in_saudi(item)
            if reach is None:
                continue
            tags = item.get("tags", {})
            name = next((tags[k] for k in NAME_KEYS if tags.get(k)), item["displayName"])
            if not kept(name, words):
                continue
            own = tags.get("brand") == tags.get("name")
            spellings = [item["displayName"], *(tags.get(k) for k in NAME_KEYS),
                         *(tags.get(k) for k in BRAND_KEYS if own), *item.get("matchNames", [])]
            spellings = sorted({s for s in spellings if s and s != name and kept(s, words)})
            # A Saudi entry beats a worldwide one; then a brand's main trade beats a sideline
            # (Walmart's fuel stations, a supermarket's pharmacy).
            rank = (reach != "sa", kind in SIDELINES)
            if name not in merchants or rank < merchants[name][0]:
                merchants[name] = (rank, {"name": name, "type": kind, "spellings": spellings})

    OUT.parent.mkdir(parents=True, exist_ok=True)
    body = {"source": "OpenStreetMap name-suggestion-index (BSD-3-Clause), via scripts/nsi_merchants.py",
            "merchants": sorted((m for _, m in merchants.values()), key=lambda m: m["name"].lower())}
    OUT.write_text(json.dumps(body, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(merchants)} merchants → {OUT}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Build the bundled Spain overview map (IGN orthophoto, zoom 5-9) as MBTiles.

Writes app/src/main/assets/spain.mbtiles. Run once from the repository root; the app copies the
file out of the APK on first launch. Sequential and polite: failed tiles are skipped and reported.
"""

import math
import sqlite3
import sys
import time
import urllib.request
from pathlib import Path

IGN_TILE_URL = (
    "https://www.ign.es/wmts/pnoa-ma?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0"
    "&LAYER=OI.OrthoimageCoverage&STYLE=default&TILEMATRIXSET=GoogleMapsCompatible"
    "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&FORMAT=image/jpeg"
)
ATTRIBUTION = "PNOA cedido por © Instituto Geográfico Nacional de España"
MIN_ZOOM, MAX_ZOOM = 5, 9
# (south, west, north, east): peninsula + Balearics + Ceuta/Melilla, and the Canaries
AREAS = [(35.2, -9.6, 44.0, 4.6), (27.5, -18.3, 29.5, -13.3)]
OUTPUT = Path("app/src/main/assets/spain.mbtiles")


def tile_x(lon, z):
    n = 2**z
    return min(n - 1, max(0, int((lon + 180.0) / 360.0 * n)))


def tile_y(lat, z):
    n = 2**z
    r = math.radians(lat)
    return min(n - 1, max(0, int((1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.pi) / 2.0 * n)))


def tiles():
    seen = set()
    for z in range(MIN_ZOOM, MAX_ZOOM + 1):
        for south, west, north, east in AREAS:
            for x in range(tile_x(west, z), tile_x(east, z) + 1):
                for y in range(tile_y(north, z), tile_y(south, z) + 1):
                    if (z, x, y) not in seen:
                        seen.add((z, x, y))
                        yield z, x, y


def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.unlink(missing_ok=True)
    db = sqlite3.connect(OUTPUT)
    db.execute("CREATE TABLE metadata (name TEXT, value TEXT)")
    db.execute("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
    db.execute("CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row)")
    db.executemany(
        "INSERT INTO metadata VALUES (?, ?)",
        [
            ("name", "spain"),
            ("format", "jpg"),
            ("type", "baselayer"),
            ("bounds", "-18.3,27.5,4.6,44.0"),
            ("minzoom", str(MIN_ZOOM)),
            ("maxzoom", str(MAX_ZOOM)),
            ("attribution", ATTRIBUTION),
        ],
    )

    todo = list(tiles())
    failed = 0
    for i, (z, x, y) in enumerate(todo, 1):
        url = IGN_TILE_URL.format(z=z, x=x, y=y)
        try:
            with urllib.request.urlopen(url, timeout=20) as resp:
                data = resp.read()
            db.execute("INSERT INTO tiles VALUES (?, ?, ?, ?)", (z, x, 2**z - 1 - y, data))  # TMS rows
        except OSError as e:
            failed += 1
            print(f"failed {z}/{x}/{y}: {e}", file=sys.stderr)
        print(f"\r{i}/{len(todo)}", end="", flush=True)
        time.sleep(0.05)
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"\n{OUTPUT}: {OUTPUT.stat().st_size / 1e6:.1f} MB, {failed} failed tiles")


if __name__ == "__main__":
    main()

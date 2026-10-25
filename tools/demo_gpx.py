#!/usr/bin/env python3
"""Generic demo routes for screenshots: OSM hiking relations (Overpass) -> one GPX per route.

Each route is clipped to its zone's bounding box, densified to a point every 10 m (so the reveal has
no gaps) and given past timestamps (spread over the last months, at walking speed), so they show up
in the tracks list with varied dates but never as the recent trail.

    python3 tools/demo_gpx.py OUT_DIR

Route data © OpenStreetMap contributors (ODbL).
"""
import json
import math
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from xml.sax.saxutils import escape

OVERPASS = "https://overpass-api.de/api/interpreter"
ZONES = {
    # name: (south, west, north, east), refs to keep (None keeps every route)
    "picos": ((43.15, -4.95, 43.28, -4.72),
              {"PR-PNPE 21", "PR-PNPE 23", "PR-PNPE 24", "PR-PNPE 18", "PR-PNPE 22", "PR-PNPE 31",
               "Trav-5", "Trav-6", "Trav-7", "PR-PNPE 16"}),
    "montserrat": ((41.575, 1.79, 41.625, 1.855), None),
}
STEP_M = 10
SPEED_MPS = 1.1


def overpass(bbox):
    query = f"[out:json][timeout:120];rel[route=hiking]({','.join(map(str, bbox))});out geom;"
    req = urllib.request.Request(OVERPASS, urllib.parse.urlencode({"data": query}).encode(),
                                 headers={"User-Agent": "foghike-demo-gpx/1.0"})
    for wait in (0, 20, 40, 80):  # the public server is often at capacity
        time.sleep(wait)
        try:
            with urllib.request.urlopen(req, timeout=200) as r:
                return json.load(r)["elements"]
        except Exception as e:
            print("overpass:", e, file=sys.stderr)
    sys.exit("overpass unavailable")


def dist(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (*a, *b))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


def chain(ways):
    """Joins the member ways in order, flipping each one to start where the previous ended."""
    line = []
    for w in ways:
        if line and dist(line[-1], w[-1]) < dist(line[-1], w[0]):
            w = w[::-1]
        line += w
    return line


def densify(points):
    out = points[:1]
    for a, b in zip(points, points[1:]):
        n = int(dist(a, b) // STEP_M)
        out += [(a[0] + (b[0] - a[0]) * i / (n + 1), a[1] + (b[1] - a[1]) * i / (n + 1)) for i in range(1, n + 1)]
        out.append(b)
    return out


def gpx(name, points, start):
    t, rows = start, []
    for i, p in enumerate(points):
        if i:
            t += timedelta(seconds=dist(points[i - 1], p) / SPEED_MPS)
        rows.append(f'<trkpt lat="{p[0]:.6f}" lon="{p[1]:.6f}"><time>{t:%Y-%m-%dT%H:%M:%SZ}</time></trkpt>')
    return ('<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="foghike demo_gpx">\n'
            f"<trk><name>{escape(name)}</name><trkseg>\n" + "\n".join(rows) + "\n</trkseg></trk></gpx>\n")


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    day = datetime.now(timezone.utc).replace(hour=8, minute=0, second=0, microsecond=0)
    n = 0
    for zone, (bbox, refs) in ZONES.items():
        s, w, nn, e = bbox
        for rel in overpass(bbox):
            tags = rel["tags"]
            if refs is not None and tags.get("ref") not in refs:
                continue
            ways = [[(g["lat"], g["lon"]) for g in m["geometry"]] for m in rel["members"]
                    if m["type"] == "way" and "geometry" in m]
            points = [p for p in densify(chain(ways)) if s <= p[0] <= nn and w <= p[1] <= e]
            if len(points) < 50:
                continue
            n += 1
            name = tags.get("name") or tags.get("ref") or f"{zone} {rel['id']}"
            (out / f"{zone}-{rel['id']}.gpx").write_text(gpx(name, points, day - timedelta(days=4 + 5 * n)))
            print(f"{zone}-{rel['id']}.gpx  {len(points)} points  {name}")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

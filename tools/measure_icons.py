# -*- coding: utf-8 -*-
"""测量每个图标在 24 网格里的实际墨迹范围（用于统一尺寸）。"""
import glob
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET

from PIL import Image

DRAWABLE = "app/src/main/res/drawable"
HTML = "E:/Workbuddy AI/manga tools/.measure.html"
PNG = "E:/Workbuddy AI/manga tools/.measure.png"
EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"

CELL = 300
ICON_PX = 240
COLS = 4
ANDROID = "{http://schemas.android.com/apk/res/android}"


def svg_body(path):
    root = ET.parse(path).getroot()
    vw = float(root.get(ANDROID + "viewportWidth", "24"))
    vh = float(root.get(ANDROID + "viewportHeight", "24"))
    parts = []
    for p in root.iter("path"):
        d = p.get(ANDROID + "pathData")
        if not d:
            continue
        fill = p.get(ANDROID + "fillColor")
        stroke = p.get(ANDROID + "strokeColor")
        sw = float(p.get(ANDROID + "strokeWidth") or 0)
        rule = p.get(ANDROID + "fillType") or "nonZero"
        cap = p.get(ANDROID + "strokeLineCap") or "butt"
        join = p.get(ANDROID + "strokeLineJoin") or "miter"
        attrs = ['d="%s"' % re.sub(r"\s+", " ", d).strip()]
        attrs.append('fill="%s"' % ("#000000" if fill else "none"))
        if rule == "evenOdd":
            attrs.append('fill-rule="evenodd"')
        if stroke and sw > 0:
            attrs.append('stroke="#000000"')
            attrs.append('stroke-width="%s"' % sw)
            attrs.append('stroke-linecap="%s"' % cap)
            attrs.append('stroke-linejoin="%s"' % join)
        parts.append("<path %s />" % " ".join(attrs))
    return vw, vh, "\n".join(parts)


def main():
    files = sorted(glob.glob(os.path.join(DRAWABLE, "ic_*.xml")))
    cells = []
    for f in files:
        vw, vh, body = svg_body(f)
        cells.append('<div class="cell"><svg viewBox="0 0 %s %s" width="%d" height="%d">%s</svg></div>'
                     % (vw, vh, ICON_PX, ICON_PX, body))
    rows = (len(files) + COLS - 1) // COLS
    html = """<!DOCTYPE html><html><head><meta charset="utf-8"><style>
body{margin:0;background:#fff}
.grid{display:grid;grid-template-columns:repeat(%d,%dpx);grid-auto-rows:%dpx}
.cell{width:%dpx;height:%dpx;display:flex;align-items:center;justify-content:center}
</style></head><body><div class="grid">%s</div></body></html>""" % (
        COLS, CELL, CELL, CELL, CELL, "\n".join(cells))

    with open(HTML, "w", encoding="utf-8") as fh:
        fh.write(html)

    subprocess.run([
        EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars",
        "--screenshot=" + PNG, "--window-size=%d,%d" % (COLS * CELL, rows * CELL),
        "file:///" + HTML.replace("\\", "/"),
    ], check=False, timeout=180)

    im = Image.open(PNG).convert("L")
    out = []
    for i, f in enumerate(files):
        name = os.path.splitext(os.path.basename(f))[0]
        col, row = i % COLS, i // COLS
        x0 = col * CELL + (CELL - ICON_PX) // 2
        y0 = row * CELL + (CELL - ICON_PX) // 2
        xs, ys = [], []
        for y in range(y0, y0 + ICON_PX):
            for x in range(x0, x0 + ICON_PX):
                if im.getpixel((x, y)) < 190:
                    xs.append(x - x0)
                    ys.append(y - y0)
        if not xs:
            out.append({"name": name, "empty": True})
            continue
        bx0, bx1 = min(xs) / ICON_PX * 24, (max(xs) + 1) / ICON_PX * 24
        by0, by1 = min(ys) / ICON_PX * 24, (max(ys) + 1) / ICON_PX * 24
        out.append({
            "name": name,
            "x": round(bx0, 2), "y": round(by0, 2),
            "w": round(bx1 - bx0, 2), "h": round(by1 - by0, 2),
            "cx": round((bx0 + bx1) / 2, 2), "cy": round((by0 + by1) / 2, 2),
        })

    with open("E:/Workbuddy AI/manga tools/.measure.json", "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1)

    for o in out:
        if o.get("empty"):
            print("%-22s EMPTY" % o["name"])
        else:
            print("%-22s x %5.2f y %5.2f  w %5.2f h %5.2f  center(%.2f,%.2f)"
                  % (o["name"], o["x"], o["y"], o["w"], o["h"], o["cx"], o["cy"]))


if __name__ == "__main__":
    main()

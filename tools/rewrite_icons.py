# -*- coding: utf-8 -*-
"""把图标统一到同一尺寸 / 同一线宽。

- 线条类图标：重写为 2dp 描边路径，几何尺寸缩放到统一基准
- 实心类图标：用 group 缩放（保持实心造型），缩放到统一基准
- 顺带修正导出图标箭头不尖锐的问题（箭头作为一条折线，尖角用 miter 合并）
"""
import glob
import json
import math
import os
import re
import xml.etree.ElementTree as ET

DRAWABLE = "app/src/main/res/drawable"
ANDROID = "{http://schemas.android.com/apk/res/android}"
INK = 20.0          # 统一后的墨迹长边（含 2dp 描边）
INDICATOR_INK = 16.0  # 指示类小箭头
STROKE = 2.0

# name -> (ops, target)
# ops: ("poly", [(x,y)...]) / ("circle", cx, cy, r) / ("arc", cx, cy, r, a1, a2)
LINE_SPECS = {
    "ic_back": ([("poly", [(19, 12), (5, 12)]),
                 ("poly", [(11, 6), (5, 12), (11, 18)])], INK),
    "ic_forward": ([("poly", [(5, 12), (19, 12)]),
                    ("poly", [(13, 6), (19, 12), (13, 18)])], INK),
    "ic_menu": ([("poly", [(4, 7), (20, 7)]),
                 ("poly", [(4, 12), (20, 12)]),
                 ("poly", [(4, 17), (20, 17)])], INK),
    "ic_close": ([("poly", [(6, 6), (18, 18)]),
                  ("poly", [(18, 6), (6, 18)])], INK),
    "ic_search": ([("circle", 10.8, 10.8, 6.6),
                   ("poly", [(15.6, 15.6), (20, 20)])], INK),
    "ic_share": ([("circle", 18, 6, 2.4), ("circle", 6, 12, 2.4), ("circle", 18, 18, 2.4),
                  ("poly", [(8.1, 10.9), (15.9, 7.1)]),
                  ("poly", [(8.1, 13.1), (15.9, 16.9)])], INK),
    "ic_export": ([("poly", [(12, 15.6), (12, 4.8)]),
                   ("poly", [(7.2, 9.0), (12, 4.8), (16.8, 9.0)]),
                   ("poly", [(5, 15.4), (5, 20.2), (19, 20.2), (19, 15.4)])], INK),
    "ic_delete": ([("poly", [(4, 7), (20, 7)]),
                   ("poly", [(9, 7), (9, 4.4), (15, 4.4), (15, 7)]),
                   ("poly", [(6.6, 7), (7.6, 20), (16.4, 20), (17.4, 7)]),
                   ("poly", [(10, 11), (10, 16)]),
                   ("poly", [(14, 11), (14, 16)])], INK),
    "ic_copy": ([("poly", [(8.8, 8.8), (20.2, 8.8), (20.2, 20.2), (8.8, 20.2), (8.8, 8.8)]),
                 ("poly", [(5, 15.2), (5, 3.8), (20.2, 3.8), (20.2, 7.4)])], INK),
    "ic_refresh": ([("arc", 12, 12, 8, 0, 300),
                    ("poly", [(20.2, 3.6), (20.2, 8.8), (15, 8.8)])], INK),
    "ic_chevron": ([("poly", [(9, 4.8), (16.2, 12), (9, 19.2)])], INDICATOR_INK),
    "ic_check": ([("poly", [(4.8, 12.4), (9.4, 17), (19.2, 6.6)])], INK),
    "ic_star_border": ([("poly", [(12, 3.4), (14.9, 9.3), (21.4, 10.2), (16.7, 14.8),
                                  (17.8, 21.2), (12, 18.2), (6.2, 21.2), (7.3, 14.8),
                                  (2.6, 10.2), (9.1, 9.3), (12, 3.4)])], INK),
    "ic_history": ([("circle", 12, 12, 9.0),
                    ("poly", [(12, 6.8), (12, 12), (16, 12)])], INK),
}

# 实心图标：直接用 group 缩放
SOLID = ["ic_arrow_drop_down", "ic_download", "ic_favorite", "ic_history_solid",
         "ic_home", "ic_pause", "ic_person", "ic_play", "ic_read",
         "ic_settings", "ic_star", "ic_triangle_down"]

# 下拉类小指示箭头：保持小巧，不跟着按钮尺寸放大
SOLID_TARGETS = {"ic_arrow_drop_down": 14.0, "ic_triangle_down": 14.0}


def tint_of(path):
    root = ET.parse(path).getroot()
    return root.get(ANDROID + "tint")


def arc_points(cx, cy, r, a1, a2, steps=48):
    pts = []
    for i in range(steps + 1):
        a = math.radians(a1 + (a2 - a1) * i / steps)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def geom_bbox(ops):
    xs, ys = [], []
    for op in ops:
        if op[0] == "poly":
            for x, y in op[1]:
                xs.append(x)
                ys.append(y)
        elif op[0] == "circle":
            _, cx, cy, r = op
            xs += [cx - r, cx + r]
            ys += [cy - r, cy + r]
        elif op[0] == "arc":
            for x, y in arc_points(*op[1:]):
                xs.append(x)
                ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def fmt(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return s if s else "0"


def build_line_xml(name, ops, target, tint):
    x0, y0, x1, y1 = geom_bbox(ops)
    w, h = x1 - x0, y1 - y0
    s = (target - STROKE) / max(w, h)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2

    def tf(p):
        return (12 + (p[0] - cx) * s, 12 + (p[1] - cy) * s)

    paths = []
    for op in ops:
        if op[0] == "poly":
            pts = [tf(p) for p in op[1]]
            d = "M%s,%s" % (fmt(pts[0][0]), fmt(pts[0][1]))
            d += "".join(" L%s,%s" % (fmt(x), fmt(y)) for x, y in pts[1:])
        elif op[0] == "circle":
            _, ox, oy, r = op
            c, r2 = tf((ox, oy)), r * s
            d = ("M%s,%s a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 -%s,0"
                 % (fmt(c[0] - r2), fmt(c[1]), fmt(r2), fmt(r2),
                    fmt(2 * r2), fmt(r2), fmt(r2), fmt(2 * r2)))
        else:
            _, ox, oy, r, a1, a2 = op
            c = tf((ox, oy))
            r2 = r * s
            p1 = (c[0] + r2 * math.cos(math.radians(a1)), c[1] + r2 * math.sin(math.radians(a1)))
            p2 = (c[0] + r2 * math.cos(math.radians(a2)), c[1] + r2 * math.sin(math.radians(a2)))
            large = 1 if abs(a2 - a1) > 180 else 0
            d = "M%s,%s A%s,%s 0 %d,1 %s,%s" % (
                fmt(p1[0]), fmt(p1[1]), fmt(r2), fmt(r2), large, fmt(p2[0]), fmt(p2[1]))
        paths.append(d)

    body = "\n".join(
        '    <path\n        android:pathData="%s"\n'
        '        android:strokeColor="#FFFFFF"\n'
        '        android:strokeWidth="%s" />' % (d, fmt(STROKE)) for d in paths)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="24"\n    android:viewportHeight="24"\n'
            '    android:tint="%s">\n%s\n</vector>\n') % (tint, body)


def wrap_solid(name, path, target, measure):
    bbox = next(o for o in measure if o["name"] == name)
    w, h = bbox["w"], bbox["h"]
    k = target / max(w, h)
    cx, cy = bbox["cx"], bbox["cy"]
    dx, dy = 12 - cx, 12 - cy
    root = ET.parse(path).getroot()
    tint = root.get(ANDROID + "tint")
    inner = []
    for p in root.iter("path"):
        attrs = []
        for key in ("pathData", "fillColor", "fillType", "strokeColor", "strokeWidth"):
            v = p.get(ANDROID + key)
            if v:
                attrs.append('        android:%s="%s"' % (key, re.sub(r"\s+", " ", v).strip()))
        inner.append("    <path\n" + "\n".join(attrs) + " />")
    if abs(k - 1) < 0.01 and abs(dx) < 0.25 and abs(dy) < 0.25:
        return None
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="24"\n    android:viewportHeight="24"\n'
            '    android:tint="%s">\n'
            '    <group\n'
            '        android:pivotX="%s"\n        android:pivotY="%s"\n'
            '        android:scaleX="%s"\n        android:scaleY="%s"\n'
            '        android:translateX="%s"\n        android:translateY="%s">\n'
            '%s\n    </group>\n</vector>\n') % (
        tint, fmt(cx), fmt(cy), fmt(k), fmt(k), fmt(dx), fmt(dy), "\n".join(inner))


def main():
    measure = json.load(open("E:/Workbuddy AI/manga tools/.measure.json", encoding="utf-8"))

    for name, (ops, target) in LINE_SPECS.items():
        path = os.path.join(DRAWABLE, name + ".xml")
        tint = tint_of(path)
        open(path, "w", encoding="utf-8").write(build_line_xml(name, ops, target, tint))
        print("line", name)

    for name in SOLID:
        path = os.path.join(DRAWABLE, name + ".xml")
        xml = wrap_solid(name, path, SOLID_TARGETS.get(name, INK), measure)
        if xml:
            open(path, "w", encoding="utf-8").write(xml)
            print("solid", name)


if __name__ == "__main__":
    main()

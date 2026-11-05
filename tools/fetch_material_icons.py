# -*- coding: utf-8 -*-
"""从 Material Icons（Filled）下载整套图标并转成 Android vector drawable。

全选/全不选用的就是 Material 的 done_all，所以整套换成同一个图标族，
尺寸、笔画粗细、留白都遵循同一套 24dp 规范。
"""
import os
import re
import subprocess
import xml.etree.ElementTree as ET

DRAWABLE = "app/src/main/res/drawable"
BASE = "https://cdn.jsdelivr.net/npm/@material-design-icons/svg@0.14.13/filled/%s.svg"
ANDROID = "{http://schemas.android.com/apk/res/android}"

MAP = {
    "ic_menu": "menu",
    "ic_close": "close",
    "ic_search": "search",
    "ic_back": "arrow_back",
    "ic_forward": "arrow_forward",
    "ic_chevron": "chevron_right",
    "ic_play": "play_arrow",
    "ic_pause": "pause",
    "ic_delete": "delete",
    "ic_favorite": "favorite",
    "ic_person": "person",
    "ic_settings": "settings",
    "ic_history_solid": "history",
    "ic_read": "menu_book",
    "ic_copy": "content_copy",
    "ic_home": "home",
    "ic_refresh": "refresh",
    "ic_star": "star",
    "ic_star_border": "star_border",
    "ic_download": "file_download",
    "ic_check": "check",
    "ic_export": "file_upload",
    "ic_share": "share",
    "ic_select_all": "done_all",
    "ic_deselect_all": "done_all",
    "ic_triangle_down": "arrow_drop_down",
    "ic_arrow_drop_down": "arrow_drop_down",
}


def fetch(name):
    url = BASE % name
    last = None
    for _ in range(3):
        p = subprocess.run(["curl", "-s", "-m", "30", url],
                           capture_output=True, text=True)
        if p.returncode == 0 and p.stdout.lstrip().startswith("<svg"):
            return p.stdout
        last = p.stdout[:120] or p.stderr[:120]
    raise RuntimeError("fetch failed %s: %s" % (name, last))


def tint_of(path):
    try:
        root = ET.parse(path).getroot()
        return root.get(ANDROID + "tint")
    except Exception:
        return None


def convert(svg):
    root = ET.fromstring(svg)
    view = root.get("viewBox", "0 0 24 24").split()
    vw, vh = view[2], view[3]
    paths = []
    for p in root.iter():
        tag = p.tag.split("}")[-1]
        if tag != "path":
            continue
        d = p.get("d")
        if not d:
            continue
        rule = p.get("fill-rule")
        paths.append((re.sub(r"\s+", " ", d).strip(), rule))
    if not paths:
        raise ValueError("no path")
    return vw, vh, paths


def build(vw, vh, paths, tint):
    body = []
    for d, rule in paths:
        extra = '\n        android:fillType="evenOdd"' if rule == "evenodd" else ""
        body.append('    <path\n        android:fillColor="#FFFFFF"\n'
                    '        android:pathData="%s"%s />' % (d, extra))
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="%s"\n    android:viewportHeight="%s"\n'
            '    android:tint="%s">\n%s\n</vector>\n') % (
        vw, vh, tint or "@color/muted", "\n".join(body))


def main():
    for drawable, name in MAP.items():
        path = os.path.join(DRAWABLE, drawable + ".xml")
        tint = tint_of(path)
        svg = fetch(name)
        vw, vh, paths = convert(svg)
        open(path, "w", encoding="utf-8").write(build(vw, vh, paths, tint))
        print("%-22s <- %-16s paths=%d" % (drawable, name, len(paths)))


if __name__ == "__main__":
    main()

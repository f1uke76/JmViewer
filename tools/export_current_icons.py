# -*- coding: utf-8 -*-
"""把当前 drawable 里的矢量图标导出成一张 PNG 预览图（用于对比 / 提意见）。"""
import glob
import os
import re
import subprocess
import xml.etree.ElementTree as ET

DRAWABLE = "app/src/main/res/drawable"
OUT_HTML = "E:/Workbuddy AI/manga tools/.icon_current.html"
OUT_PNG = "E:/Workbuddy AI/manga tools/icon_current.png"
EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"

LABELS = {
    "ic_menu": "菜单", "ic_close": "关闭", "ic_search": "搜索",
    "ic_back": "返回", "ic_forward": "前进", "ic_chevron": "列表箭头",
    "ic_play": "开始", "ic_pause": "暂停", "ic_delete": "删除",
    "ic_favorite": "收藏", "ic_person": "我的", "ic_settings": "设置",
    "ic_history_solid": "历史", "ic_history": "历史·线", "ic_read": "阅读",
    "ic_copy": "复制", "ic_home": "主页", "ic_refresh": "刷新",
    "ic_star": "星标", "ic_star_border": "星标描边", "ic_download": "下载",
    "ic_check": "完成勾", "ic_export": "导出", "ic_share": "分享",
    "ic_select_all": "全选", "ic_deselect_all": "全不选",
    "ic_triangle_down": "下拉三角", "ic_arrow_drop_down": "下拉",
}


def color_map():
    """从 colors.xml 读资源色，主题色取 primary。"""
    root = ET.parse("app/src/main/res/values/colors.xml").getroot()
    out = {}
    for c in root.findall("color"):
        out[c.get("name")] = c.text.strip()
    return out


def resolve(value, colors):
    if not value:
        return None
    v = value.strip()
    if v.startswith("#"):
        return v
    if v.startswith("@color/"):
        return colors.get(v[len("@color/"):], "#000000")
    if v.startswith("?attr/colorPrimary"):
        return colors.get("primary", "#1668E3")
    if v.startswith("?"):
        return colors.get("on_surface", "#000000")
    return v


def _path_el(p, colors, tint, indent):
    d = p.get("{http://schemas.android.com/apk/res/android}pathData")
    if not d:
        return ""
    fill = resolve(p.get("{http://schemas.android.com/apk/res/android}fillColor"), colors)
    stroke = resolve(p.get("{http://schemas.android.com/apk/res/android}strokeColor"), colors)
    sw = p.get("{http://schemas.android.com/apk/res/android}strokeWidth") or "0"
    cap = p.get("{http://schemas.android.com/apk/res/android}strokeLineCap") or "butt"
    join = p.get("{http://schemas.android.com/apk/res/android}strokeLineJoin") or "miter"
    rule = p.get("{http://schemas.android.com/apk/res/android}fillType") or "nonZero"

    if tint:
        if fill and fill.upper() in ("#FFFFFF", "#FFFFFFFF"):
            fill = tint
        if stroke and stroke.upper() in ("#FFFFFF", "#FFFFFFFF"):
            stroke = tint

    attrs = ['d="%s"' % re.sub(r"\s+", " ", d).strip()]
    attrs.append('fill="%s"' % (fill if fill else "none"))
    if rule == "evenOdd":
        attrs.append('fill-rule="evenodd"')
    if stroke and float(sw) > 0:
        attrs.append('stroke="%s"' % stroke)
        attrs.append('stroke-width="%s"' % sw)
        attrs.append('stroke-linecap="%s"' % cap)
        attrs.append('stroke-linejoin="%s"' % join)
    return "%s<path %s />" % (indent, " ".join(attrs))


def _walk(node, colors, tint, indent=""):
    out = []
    for child in node:
        tag = child.tag.split("}")[-1]
        if tag == "path":
            el = _path_el(child, colors, tint, indent)
            if el:
                out.append(el)
        elif tag == "group":
            px = float(child.get("{http://schemas.android.com/apk/res/android}pivotX") or 0)
            py = float(child.get("{http://schemas.android.com/apk/res/android}pivotY") or 0)
            sx = float(child.get("{http://schemas.android.com/apk/res/android}scaleX") or 1)
            sy = float(child.get("{http://schemas.android.com/apk/res/android}scaleY") or 1)
            tx = float(child.get("{http://schemas.android.com/apk/res/android}translateX") or 0)
            ty = float(child.get("{http://schemas.android.com/apk/res/android}translateY") or 0)
            t = "translate(%s,%s) translate(%s,%s) scale(%s,%s) translate(%s,%s)" % (
                tx, ty, px, py, sx, sy, -px, -py)
            out.append('%s<g transform="%s">' % (indent, t))
            out.extend(_walk(child, colors, tint, indent + "  "))
            out.append(indent + "</g>")
    return out


def convert(path, colors):
    tree = ET.parse(path)
    root = tree.getroot()
    tint = resolve(root.get("{http://schemas.android.com/apk/res/android}tint"), colors)
    vw = root.get("{http://schemas.android.com/apk/res/android}viewportWidth", "24")
    vh = root.get("{http://schemas.android.com/apk/res/android}viewportHeight", "24")
    body = "\n".join(_walk(root, colors, tint, "    "))
    white = bool(tint and tint.upper() in ("#FFFFFF", "#FFFFFFFF"))
    if not white:
        fills = re.findall(r'fill="([^"]+)"', body)
        strokes = re.findall(r'stroke="([^"]+)"', body)
        used = [c.upper() for c in fills + strokes if c != "none"]
        white = bool(used) and all(c in ("#FFFFFF", "#FFFFFFFF") for c in used)
    return vw, vh, body, white


def main():
    colors = color_map()
    files = sorted(glob.glob(os.path.join(DRAWABLE, "ic_*.xml")))
    cells = []
    for f in files:
        name = os.path.splitext(os.path.basename(f))[0]
        vw, vh, body, white = convert(f, colors)
        cells.append(
            '<div class="cell"><div class="ico%s"><svg viewBox="0 0 %s %s" width="72" height="72">%s</svg></div>'
            '<div class="name">%s</div><div class="file">%s</div></div>'
            % (" chip" if white else "", vw, vh, body, LABELS.get(name, name), name)
        )

    html = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><style>
body{margin:0;background:#fff;font-family:"Microsoft YaHei",sans-serif;color:#1a1a1a}
.wrap{padding:48px 60px 40px}
h1{font-size:30px;font-weight:700;margin:0 0 10px}
.sub{font-size:15px;color:#5f5e5a;margin-bottom:30px}
.grid{display:grid;grid-template-columns:repeat(6,1fr);gap:24px 12px}
.cell{text-align:center}
.ico{height:96px;display:flex;align-items:center;justify-content:center}
.ico.chip{width:96px;margin:0 auto;background:#1668E3;border-radius:20px}
.name{font-size:14px;color:#1a1a1a;margin-top:8px}
.file{font-size:11px;color:#9a9a9a;margin-top:2px}
</style></head><body><div class="wrap">
<h1>图标统一后（24dp 画布）</h1>
<div class="sub">线条类统一 2dp 描边 · 长边统一 20dp · 下拉小箭头 14dp · 导出箭头已合并成尖角</div>
<div class="grid">%s</div></div></body></html>""" % "\n".join(cells)

    with open(OUT_HTML, "w", encoding="utf-8") as fh:
        fh.write(html)

    subprocess.run([
        EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars",
        "--force-device-scale-factor=2",
        "--screenshot=" + OUT_PNG, "--window-size=1200,1120",
        "file:///" + OUT_HTML.replace("\\", "/"),
    ], check=False, timeout=120)
    print("icons", len(files), "->", OUT_PNG)


if __name__ == "__main__":
    main()

# -*- coding: utf-8 -*-
"""生成 JmViewer 图标预览 PNG。

所有图标在 24x24 网格上定义：笔画 2、平头线端、直角转角；
实心图标内部镂空 / 间隙统一 2。渲染时 4 倍超采样再缩放，保证边缘锐利。
"""
import math
from PIL import Image, ImageDraw, ImageFont

S = 4                 # 超采样倍数
GRID = 24
ICON = 96             # 预览中图标显示尺寸
COLOR = (22, 104, 227)
INK = (26, 26, 26)
MUTED = (95, 94, 90)
WHITE = (255, 255, 255)
BG = (255, 255, 255)
LW = 2


def stroke(d, pts, color=COLOR, w=LW):
    """折线：逐段描边 + 顶点补方，模拟平头直角转角。"""
    for i in range(len(pts) - 1):
        d.line([pts[i], pts[i + 1]], fill=color, width=int(w))
    if len(pts) > 2:
        half = w / 2.0
        for p in pts[1:-1]:
            d.rectangle([p[0] - half, p[1] - half, p[0] + half, p[1] + half], fill=color)


def arc_pts(cx, cy, r, a1, a2, steps=64):
    pts = []
    for i in range(steps + 1):
        a = math.radians(a1 + (a2 - a1) * i / steps)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def rot_rect(cx, cy, w, h, deg):
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    out = []
    for dx, dy in ((-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)):
        out.append((cx + dx * ca - dy * sa, cy + dx * sa + dy * ca))
    return out


def star_pts(cx, cy, r_out, r_in, points=5):
    out = []
    for i in range(points * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(-90 + i * 180.0 / points)
        out.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return out


def draw_icon(d, name):
    """在 24x24 坐标系内绘制（调用方已做缩放）。"""
    if name == "back":
        stroke(d, [(19, 12), (5, 12)])
        stroke(d, [(11, 6), (5, 12), (11, 18)])
    elif name == "forward":
        stroke(d, [(5, 12), (19, 12)])
        stroke(d, [(13, 6), (19, 12), (13, 18)])
    elif name == "menu":
        for y in (7, 12, 17):
            stroke(d, [(4, y), (20, y)])
    elif name == "close":
        stroke(d, [(6, 6), (18, 18)])
        stroke(d, [(18, 6), (6, 18)])
    elif name == "search":
        d.ellipse([10.8 - 6.6, 10.8 - 6.6, 10.8 + 6.6, 10.8 + 6.6], outline=COLOR, width=LW)
        stroke(d, [(15.6, 15.6), (20, 20)])
    elif name == "share":
        for cx, cy in ((18, 6), (6, 12), (18, 18)):
            d.ellipse([cx - 2.5, cy - 2.5, cx + 2.5, cy + 2.5], outline=COLOR, width=LW)
        stroke(d, [(8.2, 10.9), (15.8, 7.1)])
        stroke(d, [(8.2, 13.1), (15.8, 16.9)])
    elif name == "export":
        stroke(d, [(12, 15.6), (12, 3.8)])
        stroke(d, [(7.4, 8.4), (12, 3.8), (16.6, 8.4)])
        stroke(d, [(5, 15.4), (5, 20.2), (19, 20.2), (19, 15.4)])
    elif name == "delete":
        stroke(d, [(4, 7), (20, 7)])
        stroke(d, [(9.8, 7), (9.8, 3.8), (14.2, 3.8), (14.2, 7)])
        stroke(d, [(6.6, 7), (7.6, 20), (16.4, 20), (17.4, 7)])
        stroke(d, [(10, 11), (10, 16)])
        stroke(d, [(14, 11), (14, 16)])
    elif name == "copy":
        stroke(d, [(8.8, 8.8), (20.2, 8.8), (20.2, 20.2), (8.8, 20.2), (8.8, 8.8)])
        stroke(d, [(5, 15.2), (5, 3.8), (20.2, 3.8), (20.2, 7.4)])
    elif name == "refresh":
        stroke(d, arc_pts(12, 12, 8, 0, 300, 72))
        stroke(d, [(20.2, 3.6), (20.2, 8.8), (15, 8.8)])
    elif name == "chevron":
        stroke(d, [(9, 4.8), (16.2, 12), (9, 19.2)])
    elif name == "triangle":
        stroke(d, [(6.8, 9.6), (12, 14.8), (17.2, 9.6)])
    elif name == "check":
        stroke(d, [(4.8, 12.4), (9.4, 17), (19.2, 6.6)])
    elif name == "select_all":
        stroke(d, [(2.4, 13.6), (5.6, 16.8), (10.8, 10.4)])
        stroke(d, [(11.8, 14.2), (15.0, 17.4), (21.6, 6.4)])
    elif name == "home":
        d.polygon([(3.2, 11.2), (12, 3.4), (20.8, 11.2), (20.8, 20.6), (3.2, 20.6)], fill=COLOR)
        d.rectangle([9.4, 13.6, 14.6, 20.6], fill=WHITE)
    elif name == "favorite":
        d.ellipse([8.4 - 4.3, 9.4 - 4.3, 8.4 + 4.3, 9.4 + 4.3], fill=COLOR)
        d.ellipse([15.6 - 4.3, 9.4 - 4.3, 15.6 + 4.3, 9.4 + 4.3], fill=COLOR)
        d.polygon([(4.1, 11.0), (19.9, 11.0), (12, 20.6)], fill=COLOR)
    elif name == "history":
        d.ellipse([12 - 9.8, 12 - 9.8, 12 + 9.8, 12 + 9.8], fill=COLOR)
        d.polygon(rot_rect(12, 9.4, 1.8, 5.2, 0), fill=WHITE)
        d.polygon(rot_rect(13.95, 13.13, 1.8, 4.6, -60), fill=WHITE)
    elif name == "download":
        d.rectangle([11, 3, 13, 11.2], fill=COLOR)
        d.polygon([(6.2, 9.4), (17.8, 9.4), (12, 15.2)], fill=COLOR)
        d.rectangle([5, 17, 19, 19], fill=COLOR)
    elif name == "person":
        d.ellipse([12 - 4.2, 7.8 - 4.2, 12 + 4.2, 7.8 + 4.2], fill=COLOR)
        d.ellipse([3.2, 12.8, 20.8, 27], fill=COLOR)
        d.rectangle([0, 20.5, 24, 24], fill=WHITE)
    elif name == "settings":
        for i in range(8):
            a = math.radians(i * 45)
            d.polygon(rot_rect(12 + 9.6 * math.cos(a), 12 + 9.6 * math.sin(a),
                               3.4, 4.2, i * 45), fill=COLOR)
        d.ellipse([12 - 8.4, 12 - 8.4, 12 + 8.4, 12 + 8.4], fill=COLOR)
        d.ellipse([12 - 4.0, 12 - 4.0, 12 + 4.0, 12 + 4.0], fill=WHITE)
    elif name == "play":
        d.polygon([(7.6, 4.4), (19.6, 12), (7.6, 19.6)], fill=COLOR)
    elif name == "pause":
        d.rectangle([7.8, 4.4, 10.6, 19.6], fill=COLOR)
        d.rectangle([13.4, 4.4, 16.2, 19.6], fill=COLOR)
    elif name == "read":
        d.polygon([(3.4, 6.2), (7.2, 5.2), (11.2, 6.1), (11.2, 19.4),
                   (7.2, 18.5), (3.4, 19.5)], fill=COLOR)
        d.polygon([(20.6, 6.2), (16.8, 5.2), (12.8, 6.1), (12.8, 19.4),
                   (16.8, 18.5), (20.6, 19.5)], fill=COLOR)
    elif name == "star":
        d.polygon(star_pts(12, 12.4, 9.2, 3.9), fill=COLOR)
    elif name == "star_border":
        pts = star_pts(12, 12.4, 9.2, 3.9) + [star_pts(12, 12.4, 9.2, 3.9)[0]]
        stroke(d, pts)
    else:
        raise ValueError(name)


LINE_ICONS = [
    ("back", "返回"), ("forward", "前进"), ("menu", "菜单"),
    ("close", "关闭"), ("search", "搜索"), ("share", "分享"),
    ("export", "导出"), ("delete", "删除"), ("copy", "复制"),
    ("refresh", "刷新"), ("chevron", "列表箭头"), ("triangle", "下拉"),
    ("check", "完成勾"), ("select_all", "全选"), ("select_all", "全不选"),
]
SOLID_ICONS = [
    ("home", "主页"), ("favorite", "收藏"), ("history", "历史"),
    ("download", "下载"), ("person", "我的"), ("settings", "设置"),
    ("play", "开始"), ("pause", "暂停"), ("read", "阅读"),
    ("star", "星标"), ("star_border", "星标描边"),
]

COLS = 6
CELL_W = 170
LEFT = 80
TOP_A = 200
TOP_B = 750
ROW_H = 160
W = 1200
H = 1160


def render_icon(name, size):
    img = Image.new("RGBA", (GRID * S, GRID * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d = _Scaled(d, S)
    draw_icon(d, name)
    return img.resize((size, size), Image.LANCZOS)


class _Scaled:
    """把 24 网格坐标乘以 S 后再画。"""

    def __init__(self, d, s):
        self.d = d
        self.s = s

    def _p(self, pts):
        return [(x * self.s, y * self.s) for x, y in pts]

    def line(self, pts, fill=None, width=1):
        self.d.line(self._p(pts), fill=fill, width=int(width * self.s))

    def rectangle(self, box, fill=None):
        x1, y1, x2, y2 = box
        self.d.rectangle([x1 * self.s, y1 * self.s, x2 * self.s, y2 * self.s], fill=fill)

    def ellipse(self, box, fill=None, outline=None, width=1):
        x1, y1, x2, y2 = box
        self.d.ellipse([x1 * self.s, y1 * self.s, x2 * self.s, y2 * self.s],
                       fill=fill, outline=outline,
                       width=int(width * self.s) if outline else 0)

    def polygon(self, pts, fill=None):
        self.d.polygon(self._p(pts), fill=fill)


def stroke(d, pts, color=COLOR, w=LW):
    for i in range(len(pts) - 1):
        d.line([pts[i], pts[i + 1]], fill=color, width=w)
    if len(pts) > 2:
        half = w / 2.0
        for p in pts[1:-1]:
            d.rectangle([p[0] - half, p[1] - half, p[0] + half, p[1] + half], fill=color)


def font(size, bold=False):
    path = "C:/Windows/Fonts/msyhbd.ttc" if bold else "C:/Windows/Fonts/msyh.ttc"
    return ImageFont.truetype(path, size)


def main():
    canvas = Image.new("RGB", (W * 2, H * 2), BG)
    d = ImageDraw.Draw(canvas)
    k = 2  # 画布超采样

    d.text((LEFT * k, 48 * k), "JmViewer 图标设计预览", font=font(34 * k, True), fill=INK)
    d.text((LEFT * k, 96 * k),
           "24dp 画布 · 2dp 笔画 · 平头线端 / 直角转角 · 实心图标留 2dp 镂空",
           font=font(20 * k), fill=MUTED)

    d.text((LEFT * k, 160 * k), "操作图标 · 2dp 细线", font=font(23 * k, True), fill=COLOR)

    def draw_row(items, top, color=COLOR):
        for i, (name, label) in enumerate(items):
            cx = LEFT + CELL_W * i + CELL_W // 2
            icon = render_icon(name, ICON * k)
            canvas.paste(icon, (int((cx - ICON / 2) * k), int(top * k)), icon)
            d.text((cx * k, (top + ICON + 34) * k), label,
                   font=font(19 * k), fill=MUTED, anchor="mm")

    draw_row(LINE_ICONS[0:6], TOP_A)
    draw_row(LINE_ICONS[6:12], TOP_A + ROW_H)
    draw_row(LINE_ICONS[12:15], TOP_A + ROW_H * 2)

    d.text((LEFT * k, 700 * k), "导航与状态图标 · 实心 + 2dp 镂空", font=font(23 * k, True), fill=COLOR)
    draw_row(SOLID_ICONS[0:6], TOP_B)
    draw_row(SOLID_ICONS[6:11], TOP_B + ROW_H)

    d.text((LEFT * k, 1100 * k),
           "缩放规则：图标按视图尺寸整体缩放（侧边栏 20dp、工具栏 24dp），笔画比例不变；暗色模式仅换色，形状不变。",
           font=font(19 * k), fill=MUTED)

    out = canvas.resize((W, H), Image.LANCZOS)
    out.save("E:/Workbuddy AI/manga tools/icon_review.png", "PNG")
    print("saved", out.size)


if __name__ == "__main__":
    main()

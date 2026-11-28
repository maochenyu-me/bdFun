"""重生成二改包的启动图标：白底黑图案，debug / release 各带角标。

    python tools/gen_launcher_icons.py

输出三套（同名资源由 buildType sourceSet 覆盖 main）：
    app/src/main/res       → 无角标（-Ppkg= 覆盖包名时用）
    app/src/debug/res      → .debug（applicationIdSuffix=.debug）
    app/src/release/res    → .fork（applicationIdSuffix=.fork）
背景色在 values/colors.xml 的 ic_launcher_background（#FFFFFF）。
"""

import os
from PIL import Image, ImageDraw, ImageFont

FONT = "C:/Windows/Fonts/arialbd.ttf"
INK = (13, 13, 13, 255)
WHITE = (255, 255, 255, 255)

# 1080 画布 = 108dp @10x；安全区半径 330px，内容控制在 290px 内
BASE = 540.0

TARGETS = (("main", "app/src/main/res", ""),
           ("debug", "app/src/debug/res", ".debug"),
           ("fork", "app/src/release/res", ".fork"))

DPIS = (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192))


def draw_mark(img, s=1.0, cx=BASE, cy=BASE, badge=""):
    d = ImageDraw.Draw(img)
    lw = max(2, int(34 * s))

    def X(v):
        return cx + (v - BASE) * s

    def Y(v):
        return cy + (v - BASE) * s

    # 天线：屏幕上沿两侧向外上方伸出，圆头
    for x0, x1 in ((400, 330), (680, 750)):
        d.line([(X(x0), Y(372)), (X(x1), Y(268))], fill=INK, width=lw)
        r = lw / 2
        d.ellipse([X(x1) - r, Y(268) - r, X(x1) + r, Y(268) + r], fill=INK)

    d.rounded_rectangle([X(290), Y(372), X(790), Y(668)], radius=76 * s, outline=INK, width=lw)

    f = ImageFont.truetype(FONT, int(92 * s))
    tw = d.textlength("bdFun", font=f)
    d.text((X(BASE) - tw / 2, Y(520)), "bdFun", font=f, fill=INK, anchor="lm")

    if badge:
        fb = ImageFont.truetype(FONT, int(106 * s))
        bw = d.textlength(badge, font=fb)
        d.text((X(BASE) - bw / 2, Y(770)), badge, font=fb, fill=INK, anchor="lm")
    return img


def adaptive_foreground(badge, size=1080):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    return draw_mark(img, s=size / 1080.0, cx=size / 2, cy=size / 2, badge=badge)


def legacy_icon(badge, side):
    big = side * 6
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * 0.225), fill=WHITE)
    draw_mark(img, s=(big / 1080.0) * 0.78, cx=big / 2, cy=big / 2, badge=badge)
    return img.resize((side, side), Image.LANCZOS)


def main():
    for _tag, root, badge in TARGETS:
        nodpi = os.path.join(root, "drawable-nodpi")
        os.makedirs(nodpi, exist_ok=True)
        adaptive_foreground(badge).save(os.path.join(nodpi, "ic_launcher_adaptive_foreground.png"))
        for dpi, side in DPIS:
            folder = os.path.join(root, f"mipmap-{dpi}")
            os.makedirs(folder, exist_ok=True)
            legacy_icon(badge, side).save(os.path.join(folder, "ic_launcher.png"))
        print("written", root)


if __name__ == "__main__":
    main()

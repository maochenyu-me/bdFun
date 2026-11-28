"""重生成二改包的品牌图形：白底黑图案启动图标 + 单色版开屏图形。

    python tools/gen_launcher_icons.py

启动图标三套（同名资源由 buildType sourceSet 覆盖 main）：
    app/src/main/res       → 无角标（-Ppkg= 覆盖包名、不带后缀时用）
    app/src/debug/res      → .debug（applicationIdSuffix=.debug）
    app/src/release/res    → .fork（applicationIdSuffix=.fork）
背景色在 values/colors.xml 的 ic_launcher_background（#FFFFFF）。

单色图形（应用内按主题 tint，不依赖夜间资源）：
    bdfun_splash.png        开屏大字，转成白色描边剪影（SplashWallpaper 里 tint 黑/白）
    ic_mark_mono.png        引导页 logo，白色线稿（BdFunMark 里 tint 成 onSurface）
系统开屏图标不是这里生成的：它用矢量 drawable/ic_splash_mark.xml，线条色取
@color/splash_mark_ink（values / values-night 各一份）。位图放进去会被按像素当 dp
裁边，所以那边必须是矢量。
"""

import os
from PIL import Image, ImageDraw, ImageFont

FONT = "C:/Windows/Fonts/arialbd.ttf"
INK = (13, 13, 13, 255)
LIGHT = (255, 255, 255, 255)
WHITE = (255, 255, 255, 255)

# 1080 画布 = 108dp @10x；安全区半径 330px，内容控制在 290px 内
BASE = 540.0

TARGETS = (("main", "app/src/main/res", ""),
           ("debug", "app/src/debug/res", ".debug"),
           ("fork", "app/src/release/res", ".fork"))

DPIS = (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192))


def draw_mark(img, s=1.0, cx=BASE, cy=BASE, badge="", ink=INK):
    d = ImageDraw.Draw(img)
    lw = max(2, int(34 * s))

    def X(v):
        return cx + (v - BASE) * s

    def Y(v):
        return cy + (v - BASE) * s

    # 天线：屏幕上沿两侧向外上方伸出，圆头
    for x0, x1 in ((400, 330), (680, 750)):
        d.line([(X(x0), Y(372)), (X(x1), Y(268))], fill=ink, width=lw)
        r = lw / 2
        d.ellipse([X(x1) - r, Y(268) - r, X(x1) + r, Y(268) + r], fill=ink)

    d.rounded_rectangle([X(290), Y(372), X(790), Y(668)], radius=76 * s, outline=ink, width=lw)

    f = ImageFont.truetype(FONT, int(92 * s))
    tw = d.textlength("bdFun", font=f)
    d.text((X(BASE) - tw / 2, Y(520)), "bdFun", font=f, fill=ink, anchor="lm")

    if badge:
        fb = ImageFont.truetype(FONT, int(106 * s))
        bw = d.textlength(badge, font=fb)
        d.text((X(BASE) - bw / 2, Y(770)), badge, font=fb, fill=ink, anchor="lm")
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


def centered_mark(size, fill_ratio, ink=INK):
    """画一次再按内容裁边，缩放到画面 fill_ratio，避免不同用途各留一套留白。"""
    tmp = Image.new("RGBA", (1080, 1080), (0, 0, 0, 0))
    draw_mark(tmp, ink=ink)
    crop = tmp.crop(tmp.getbbox())
    scale = (size * fill_ratio) / max(crop.size)
    crop = crop.resize((max(1, int(crop.width * scale)), max(1, int(crop.height * scale))), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.alpha_composite(crop, ((size - crop.width) // 2, (size - crop.height) // 2))
    return out


def whiten_in_place(path):
    """描边图取 alpha 转纯白剪影：原图是透明底 + 单色描边，换色只看 alpha。"""
    im = Image.open(path).convert("RGBA")
    out = Image.new("RGBA", im.size, (255, 255, 255, 0))
    out.putalpha(im.split()[3])
    out.save(path)
    print("whitened", path)


def main():
    res = "app/src/main/res"
    for _tag, root, badge in TARGETS:
        nodpi = os.path.join(root, "drawable-nodpi")
        os.makedirs(nodpi, exist_ok=True)
        adaptive_foreground(badge).save(os.path.join(nodpi, "ic_launcher_adaptive_foreground.png"))
        for dpi, side in DPIS:
            folder = os.path.join(root, f"mipmap-{dpi}")
            os.makedirs(folder, exist_ok=True)
            legacy_icon(badge, side).save(os.path.join(folder, "ic_launcher.png"))
        print("icons written", root)

    whiten_in_place(os.path.join(res, "drawable-nodpi", "bdfun_splash.png"))

    # 引导页 logo：保持原来「内容占 2/3」的留白，BdFunMark 里按 1.42 倍放大铺满。
    centered_mark(432, 2 / 3, ink=LIGHT).save(os.path.join(res, "drawable-nodpi", "ic_mark_mono.png"))
    print("mono marks written")


if __name__ == "__main__":
    main()

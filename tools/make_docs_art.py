#!/usr/bin/env python3
"""Regenerate the README artwork in docs/shots from live Paparazzi snapshots.

Identity: deepViolet orbit gradient, dotted orbit rings with player-palette
planets, device mockups with soft glow + drop shadow. Accents rotate per
hero: dial=purple, teams=teal, race=gold.
"""
import math, os
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import numpy as np

import pathlib
REPO = pathlib.Path(__file__).resolve().parents[1]
SNAPS = str(REPO / "app/src/test/snapshots/images")
OUT = str(REPO / "docs/shots")
os.makedirs(OUT, exist_ok=True)

FONTS = "/usr/share/fonts/noto"
F_BLACK = ImageFont.truetype(f"{FONTS}/NotoSans-Black.ttf", 100)
F_BOLD = ImageFont.truetype(f"{FONTS}/NotoSans-Bold.ttf", 100)
F_REG = ImageFont.truetype(f"{FONTS}/NotoSans-Regular.ttf", 100)

def font(size, weight="bold"):
    f = {"black": F_BLACK, "bold": F_BOLD, "reg": F_REG}[weight]
    return f.font_variant(size=size)

# deep orbit backdrop ------------------------------------------------------
def background(w, h, tint=(0x6A, 0x3F, 0xC8), glow2=(0x18, 0x8F, 0xB0)):
    """Vertical deep-space gradient + two soft radial glows."""
    top = np.array([0x1D, 0x14, 0x39], float)   # violet night
    bot = np.array([0x0B, 0x0A, 0x1D], float)   # near-black indigo
    t = (np.linspace(0, 1, h) ** 1.25)[:, None, None]
    base = (1 - t) * top[None, None, :] + t * bot[None, None, :]
    img = np.repeat(base, w, axis=1).reshape(h, w, 3)

    def radial(cx, cy, rad, color, strength):
        yy, xx = np.mgrid[0:h, 0:w]
        d2 = ((xx - cx) ** 2 + (yy - cy) ** 2) / (rad * rad)
        alpha = np.exp(-d2 * 1.4) * strength
        nonlocal img
        img = img + alpha[..., None] * np.array(color, float)
    radial(0.02 * w, -0.06 * h, 0.75 * w, tint, 0.36)
    radial(1.05 * w, 1.02 * h, 0.65 * w, glow2, 0.16)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").convert("RGBA")

# orbit motif ---------------------------------------------------------------
PAL = [0xE53935, 0x43A047, 0x1E88E5, 0xFBC02D, 0xAB47BC, 0x00897B, 0xFD7E14,
       0x3949AB, 0xEC407A, 0x00ACC1, 0xF5F5F5, 0x9CCC65]
def hx(v): return (v >> 16 & 255, v >> 8 & 255, v & 255)

def ellipse_pt(cx, cy, rx, ry, deg, t):
    a = math.radians(deg)
    x = rx * math.cos(t); y = ry * math.sin(t)
    return (cx + x * math.cos(a) - y * math.sin(a),
            cy + x * math.sin(a) + y * math.cos(a))

def dotted_ellipse(dr, cx, cy, rx, ry, deg, color, alpha, dot=2.4, every=2):
    n = int((2.6 * math.pi * max(rx, ry)) / 7)
    for i in range(n):
        if i % every: continue
        x, y = ellipse_pt(cx, cy, rx, ry, deg, i / n * 2 * math.pi)
        r = dot * (1 - 0.25 * (math.sin(i / n * 2 * math.pi) ** 2))
        dr.ellipse([x - r, y - r, x + r, y + r], fill=color + (alpha,))

def planet(dr, x, y, r, color, glow=True):
    if glow:
        dr.ellipse([x - r*1.9, y - r*1.9, x + r*1.9, y + r*1.9], fill=color + (38,))
    dr.ellipse([x - r, y - r, x + r, y + r], fill=color + (255,))

def orbit_motif(img, cx, cy, rx, ry, deg, accent, palette=(3, 4, 5, 1, 8), flip=False, alpha=150):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    dr = ImageDraw.Draw(layer)
    secondary = (0x9A, 0x86, 0xF2) if not flip else (0x35, 0xC9, 0xC0)
    dotted_ellipse(dr, cx, cy, rx, ry, deg, accent, alpha, dot=2.6)
    dotted_ellipse(dr, cx + rx*0.04, cy + ry*0.10, rx*0.78, ry*0.72, deg, secondary, int(alpha*0.8), dot=2.0)
    n = len(palette)
    for i, p in enumerate(palette):
        t = (i / n) * 2 * math.pi + (0.35 if i % 2 else -0.2)
        x, y = ellipse_pt(cx, cy, rx, ry, deg, t)
        planet(dr, x, y, 9, hx(PAL[p]))
    img.alpha_composite(layer)
    return img

# device mockups -------------------------------------------------------------
def snap(name):
    p = f"{SNAPS}/com.scoreorbit.android_ScreenshotsTest_{name}.png"
    d = open(p, "rb").read()
    e = d.rfind(b"IEND") + 8
    import io
    return Image.open(io.BytesIO(d[:e])).convert("RGB")

def device(snapimg, content_h, accent=(0x9D, 0x8C, 0xE0), glow=True):
    """Rounded-corner mockup with border, outer glow and drop shadow.
    Returns RGBA whose center is the screen content (canvas is larger)."""
    W, H = snapimg.size                      # 461 x 1000
    s = content_h / H
    cw, ch = round(W * s), content_h
    content = snapimg.resize((cw, ch), Image.LANCZOS)
    pad = round(ch * 0.055) + 30             # room for glow/shadow
    canvas = Image.new("RGBA", (cw + 2 * pad, ch + 2 * pad), (0, 0, 0, 0))
    rad = round(ch * 0.052)
    x0, y0 = pad, pad

    # drop shadow
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([x0+6, y0+30, x0+cw-6, y0+ch+34],
                                             rad, fill=(0, 0, 0, 120))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(26)))

    if glow:  # accent halo behind the phone
        halo = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        ImageDraw.Draw(halo).rounded_rectangle([x0, y0, x0+cw, y0+ch],
                                               rad, fill=accent + (110,))
        canvas.alpha_composite(halo.filter(ImageFilter.GaussianBlur(17)))

    body = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    body.paste(content, (x0, y0))
    mask = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([x0, y0, x0+cw, y0+ch], rad, fill=255)
    canvas.paste(body, (0, 0), mask)

    dr = ImageDraw.Draw(canvas)
    dr.rounded_rectangle([x0, y0, x0+cw, y0+ch], rad,
                         outline=accent + (235,), width=max(3, round(ch*0.004)))
    dr.rounded_rectangle([x0-2, y0-2, x0+cw+2, y0+ch+2], rad+2,
                         outline=(255, 255, 255, 26), width=1)
    return canvas

def place(img, dev, cx, cy, deg=0.0):
    if deg: dev = dev.rotate(deg, expand=True, resample=Image.BICUBIC)
    img.alpha_composite(dev, (round(cx - dev.width/2), round(cy - dev.height/2)))

# typography ----------------------------------------------------------------
def text(dr, xy, s, f, fill=(255, 255, 255, 255), anchor=None, tracking=0):
    if tracking <= 0:
        dr.text(xy, s, font=f, fill=fill, anchor=anchor)
        return
    x, y = xy
    for ch in s:
        dr.text((x, y), ch, font=f, fill=fill, anchor="ls")
        x += dr.textlength(ch, font=f) + tracking

def textlen(dr, s, f, tracking=0):
    return dr.textlength(s, font=f) + tracking * max(0, len(s) - 1)

def bullet_row(dr, x, y, label, color, size=30):
    dr.ellipse([x - 11, y - 11, x + 11, y + 11], fill=color + (255,))
    text(dr, (x + 42, y), label, font(size, "reg"), (0xE3, 0xDF, 0xF8, 255), anchor="lm")

# ---------------------------------------------------------------------------
ACC_PU = (0xA9, 0x8B, 0xF5)   # purple
ACC_TE = (0x2D, 0xD9, 0xC0)   # teal
ACC_GO = (0xFF, 0xD5, 0x4F)   # gold
ACC_BL = (0x64, 0xA8, 0xF2)   # blue

def wrap(dr, s, f, maxw):
    lines, cur = [], ""
    for w in s.split():
        trial = (cur + " " + w).strip()
        if dr.textlength(trial, font=f) <= maxw:
            cur = trial
        else:
            lines.append(cur); cur = w
    if cur: lines.append(cur)
    return lines

def hero_hired(dev_name, eyebrow, title, body, bullets, accent, pal):
    img = background(1600, 940, tint=accent, glow2=(0x18, 0x8F, 0xB0) if accent==ACC_TE else (0x4C, 0x2A, 0xA8))
    orbit_motif(img, 190, 1230, 590, 330, 14, accent, palette=pal, alpha=115)
    dr = ImageDraw.Draw(img)
    x = 108
    body_f = font(35, "reg")
    title_f = font(78, "black")
    text(dr, (x, 142), " ".join(eyebrow.upper()), font(30, "bold"), accent + (255,), tracking=10)
    text(dr, (x, 262), title, title_f, (255, 255, 255), anchor="ls")
    y = 380
    for line in wrap(dr, body, body_f, 860):
        text(dr, (x, y), line, body_f, (0xC9, 0xC4, 0xE8, 255), anchor="ls")
        y += 54
    y += 148
    for i, b in enumerate(bullets):
        bullet_row(dr, x + 11, y, b, hx(PAL[pal[i % len(pal)]]))
        y += 96
    dev = device(snap(dev_name), 852, accent)
    place(img, dev, 1230, 470, -3)
    return img

def tile(dev_name, accent):
    img = background(640, 1140, tint=accent, glow2=(0x10, 0x6B, 0x86))
    orbit_motif(img, 40, 1210, 430, 260, 12, accent, palette=(4, 1, 3, 8))
    orbit_motif(img, 640, 30, 200, 120, -8, (0x8A, 0x76, 0xE8), palette=(2, 8))
    dev = device(snap(dev_name), 992, accent)
    place(img, dev, 320, 552)
    return img

def hr_img():
    w, h = 2000, 34
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    cols = [(0xA9, 0x8B, 0xF5), (0x64, 0xA8, 0xF2), (0x2D, 0xD9, 0xC0)]
    for x in range(w):
        t = x / w
        a = int(255 * min(1, 4 * min(t, 1 - t)))
        i = min(2, int(t * 3)); f = t * 3 - i
        c = cols[i] if i < 2 else cols[2]
        c2 = cols[i + 1] if i < 2 else cols[2]
        col = tuple(int(c[j] + (c2[j] - c[j]) * f) for j in range(3))
        dr.line([(x, 16), (x, 17)], fill=col + (a if a > 40 else 0,))
    dr.ellipse([w//2 - 9, 8, w//2 + 9, 26], fill=cols[0] + (255,))
    dr.ellipse([w//2 - 16, 1, w//2 + 16, 33], fill=cols[0] + (60,))
    return img

def header():
    img = background(2000, 880, tint=(0x6A, 0x3F, 0xC8), glow2=(0x18, 0x8F, 0xB0))
    orbit_motif(img, 700, 1120, 820, 420, 20, ACC_PU, palette=(4, 6, 2, 8, 1), alpha=110)
    dr = ImageDraw.Draw(img)
    x = 120
    text(dr, (x, 266), "SCORE", font(166, "black"), (255, 255, 255), tracking=2)
    text(dr, (x, 446), "ORBIT", font(166, "black"), (0xB9, 0x9C, 0xFF), tracking=2)
    text(dr, (x, 678), "Ring scorekeeper for game night — spin to score.",
         font(46, "reg"), (0xDA, 0xD5, 0xF6, 255), anchor="lm")
    feats = "1–12 PLAYERS · TEAMS · TIMERS · TROPHIES"
    text(dr, (x, 766), feats, font(30, "bold"), (0x9A, 0x93, 0xC8, 255), anchor="lm", tracking=4)
    b1 = device(snap("board"), 690, ACC_PU)
    place(img, b1, 1385, 408, -7)
    b2 = device(snap("leaderboard"), 560, ACC_GO)
    place(img, b2, 1725, 570, 7)
    for px, py, c in [(660, 180, 3), (1055, 250, 8), (1250, 130, 5), (1005, 866, 11), (1935, 140, 9)]:
        planet(dr, px, py, 11, hx(PAL[c]))
    return img

def showcase():
    img = background(2115, 1170, tint=(0x6A, 0x3F, 0xC8), glow2=(0x18, 0x8F, 0xB0))
    orbit_motif(img, 190, 1290, 720, 400, 16, ACC_PU, palette=(1, 4, 2, 8))
    orbit_motif(img, 1990, -40, 420, 240, -14, ACC_TE, palette=(6, 8, 3))
    names = ["board", "fullHouseBoard", "settings", "onboardingTableSetup", "leaderboard", "scoreHistory"]
    thumbs = [device(snap(n), 520, ACC_PU) for n in names]
    for i, th in enumerate(thumbs):
        y = 585 + (34 * math.sin(i * 0.9 + 1.2))
        place(img, th, 170 + i * 350, y, 2.5 * math.cos(i * 0.9))
    return img

# generate -------------------------------------------------------------------
out = {}
out["header.png"] = header()
for name, dev, ey, ti, bo, bu, acc, pal in [
    ("hero-score.png", "board", "the dial", "Spin to score",
     "The board stays on the table and the dial does the arithmetic — drag around the ring the way you'd dial an old rotary phone.",
     ["A full turn banks your points", "Tap a dot for a quick score",
      "Long-press and drop a score on another seat to swap"],
     ACC_PU, [6, 2, 8]),
    ("hero-teams.png", "fullHouseBoard", "teams", "Split the table",
     "Everyone still scores individually while each area carries its live team total beside the dial.",
     ["Pick a count and players are dealt out evenly",
      "Drag a score into another area to move them",
      "Two teams split top and bottom, more get quadrants"],
     ACC_TE, [5, 6, 3]),
    ("hero-race.png", "leaderboard", "standings", "Know who's winning",
     "A Score Goal ends the game in confetti — or leave it blank and play endless.",
     ["Medal rows, ties share a rank", "Winner buttons flip Highest / Lowest",
      "Score history keeps the ledger with undo and redo"],
     ACC_BL, [3, 4, 2]),
]:
    out[name] = hero_hired(dev, ey, ti, bo, bu, acc, pal)

for name, dev, acc in [
    ("tile-board.png", "board", ACC_PU),
    ("tile-teams.png", "fullHouseBoard", ACC_TE),
    ("tile-settings.png", "settings", ACC_BL),
    ("tile-players.png", "onboardingTableSetup", ACC_GO),
    ("tile-history.png", "scoreHistory", ACC_TE),
    ("tile-leaderboard.png", "leaderboard", ACC_GO),
]:
    out[name] = tile(dev, acc)

out["hr.png"] = hr_img()
out["showcase.png"] = showcase()

for fn, im in out.items():
    if fn == "hr.png":
        im.save(f"{OUT}/{fn}")           # keep alpha for the divider
    else:
        im.convert("RGB").save(f"{OUT}/{fn}", optimize=True)
    print(fn, im.size)

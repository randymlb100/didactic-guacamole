from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "marketing" / "store"
STORE.mkdir(parents=True, exist_ok=True)

FONT_DIR = Path("C:/Windows/Fonts")


def font(name, size):
    path = FONT_DIR / name
    if path.exists():
        return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


FONT_BLACK = font("arialbd.ttf", 72)
FONT_BOLD = font("arialbd.ttf", 26)
FONT_MED = font("arial.ttf", 22)
FONT_LOGO = font("arialbd.ttf", 260)
FONT_LOGO_SMALL = font("arialbd.ttf", 58)


def draw_logo(size, output):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    pad = int(size * 0.102)
    radius = int(size * 0.18)

    for y in range(pad, size - pad):
        t = (y - pad) / max(1, size - pad * 2)
        r = int(23 * (1 - t) + 15 * t)
        g = int(52 * (1 - t) + 32 * t)
        b = int(92 * (1 - t) + 25 * t)
        draw.line((pad, y, size - pad, y), fill=(r, g, b, 255), width=1)

    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((pad, pad, size - pad, size - pad), radius=radius, fill=255)
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg.alpha_composite(img)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(bg, (0, 0), mask)
    draw = ImageDraw.Draw(out)

    inset = int(size * 0.156)
    draw.rounded_rectangle(
        (inset, inset, size - inset, size - inset),
        radius=int(size * 0.13),
        outline=(255, 248, 232, 36),
        width=max(3, size // 128),
    )

    logo_font = font("arialbd.ttf", int(size * 0.28))
    bbox = draw.textbbox((0, 0), "LN", font=logo_font)
    x = (size - (bbox[2] - bbox[0])) // 2
    y = int(size * 0.33)
    draw.text((x + 2, y + 3), "LN", font=logo_font, fill=(6, 18, 14, 70))
    draw.text((x, y), "L", font=logo_font, fill=(255, 248, 232, 255))
    l_width = draw.textlength("L", font=logo_font)
    draw.text((x + l_width - int(size * 0.025), y), "N", font=logo_font, fill=(248, 193, 90, 255))
    draw.rounded_rectangle(
        (int(size * 0.28), int(size * 0.735), int(size * 0.72), int(size * 0.795)),
        radius=int(size * 0.03),
        fill=(255, 248, 232, 230),
    )
    out.save(STORE / output)


def draw_feature_graphic():
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), (15, 32, 25))
    draw = ImageDraw.Draw(img)

    for x in range(w):
        t = x / (w - 1)
        if t < 0.55:
            k = t / 0.55
            color = (
                int(15 * (1 - k) + 23 * k),
                int(32 * (1 - k) + 52 * k),
                int(25 * (1 - k) + 92 * k),
            )
        else:
            k = (t - 0.55) / 0.45
            color = (
                int(23 * (1 - k) + 17 * k),
                int(52 * (1 - k) + 130 * k),
                int(92 * (1 - k) + 99 * k),
            )
        draw.line((x, 0, x, h), fill=color)

    draw.polygon([(0, 420), (150, 392), (360, 412), (540, 398), (740, 360), (1024, 330), (1024, 500), (0, 500)], fill=(37, 76, 61))

    draw.rounded_rectangle((68, 88, 184, 204), radius=28, fill=(232, 111, 46))
    draw.text((94, 111), "LN", font=FONT_LOGO_SMALL, fill=(255, 248, 232))

    draw.text((68, 226), "LotteryNet Pro", font=FONT_BLACK, fill=(255, 248, 232))
    draw.text((72, 286), "Ventas, resultados y recargas", font=FONT_BOLD, fill=(214, 230, 220))
    draw.text((72, 324), "Control diario para bancas y cajas.", font=FONT_MED, fill=(183, 202, 190))

    draw.rounded_rectangle((640, 70, 900, 430), radius=28, fill=(17, 40, 32))
    draw.rounded_rectangle((658, 92, 882, 408), radius=18, fill=(248, 242, 231))
    draw.rounded_rectangle((678, 118, 764, 132), radius=7, fill=(23, 52, 92))
    draw.rounded_rectangle((790, 118, 842, 132), radius=7, fill=(248, 193, 90))
    draw.rounded_rectangle((678, 154, 842, 188), radius=8, fill=(255, 255, 255))
    for y in (204, 272):
        draw.rounded_rectangle((678, y, 752, y + 52), radius=10, fill=(255, 255, 255))
        draw.rounded_rectangle((768, y, 842, y + 52), radius=10, fill=(255, 255, 255))
    draw.rounded_rectangle((768, 272, 842, 324), radius=10, fill=(248, 193, 90))
    draw.rounded_rectangle((678, 346, 842, 376), radius=9, fill=(17, 130, 99))

    draw.rounded_rectangle((870, 116, 954, 256), radius=10, fill=(255, 248, 232))
    for y, line_w in ((144, 52), (164, 38), (192, 52), (212, 34)):
        draw.rounded_rectangle((886, y, 886 + line_w, y + 8), radius=4, fill=(23, 52, 92))
    draw.ellipse((901, 223, 923, 245), fill=(232, 111, 46))

    img.save(STORE / "lotterynet-pro-feature-graphic-1024x500.png")


def draw_store_screenshot():
    w, h = 1080, 1920
    img = Image.new("RGB", (w, h), (244, 247, 251))
    draw = ImageDraw.Draw(img)
    title_font = font("arialbd.ttf", 64)
    h2_font = font("arialbd.ttf", 38)
    body_font = font("arial.ttf", 28)
    small_font = font("arialbd.ttf", 22)
    number_font = font("arialbd.ttf", 48)

    draw.rectangle((0, 0, w, 190), fill=(23, 52, 92))
    draw.text((56, 54), "LotteryNet Pro", font=title_font, fill=(255, 248, 232))
    draw.text((58, 128), "Caja principal", font=body_font, fill=(214, 230, 220))

    draw.rounded_rectangle((56, 230, 1024, 408), radius=18, fill=(255, 255, 255), outline=(215, 226, 239), width=2)
    draw.text((88, 268), "Venta del dia", font=h2_font, fill=(7, 17, 31))
    draw.text((88, 326), "RD$ 18,450", font=number_font, fill=(17, 130, 99))
    draw.rounded_rectangle((730, 278, 958, 350), radius=12, fill=(17, 130, 99))
    draw.text((772, 296), "Vender", font=h2_font, fill=(255, 255, 255))

    y = 452
    sections = [
        ("Loterias activas", ["Nacional", "Leidsa", "Real", "La Primera"]),
        ("Jugada", ["Numero", "Monto", "Pale", "Quiniela"]),
    ]
    for heading, items in sections:
        draw.text((56, y), heading, font=h2_font, fill=(7, 17, 31))
        y += 62
        x = 56
        for item in items:
            draw.rounded_rectangle((x, y, x + 220, y + 76), radius=12, fill=(255, 255, 255), outline=(215, 226, 239), width=2)
            draw.text((x + 24, y + 24), item, font=small_font, fill=(23, 52, 92))
            x += 242
        y += 122

    draw.text((56, y), "Ticket en curso", font=h2_font, fill=(7, 17, 31))
    y += 62
    draw.rounded_rectangle((56, y, 1024, y + 430), radius=18, fill=(255, 255, 255), outline=(215, 226, 239), width=2)
    rows = [("Nacional", "25", "RD$ 50"), ("Leidsa", "407", "RD$ 25"), ("Real", "12-18", "RD$ 100"), ("La Primera", "74", "RD$ 30")]
    ry = y + 34
    for name, num, amount in rows:
        draw.text((92, ry), name, font=body_font, fill=(7, 17, 31))
        draw.text((474, ry), num, font=body_font, fill=(23, 52, 92))
        draw.text((760, ry), amount, font=body_font, fill=(17, 130, 99))
        draw.line((92, ry + 52, 988, ry + 52), fill=(229, 236, 244), width=2)
        ry += 86

    y += 480
    draw.text((56, y), "Acciones", font=h2_font, fill=(7, 17, 31))
    y += 64
    for i, label in enumerate(["Imprimir", "Resultados", "Recargas"]):
        x = 56 + i * 322
        fill = [(23, 52, 92), (198, 137, 26), (232, 111, 46)][i]
        draw.rounded_rectangle((x, y, x + 290, y + 118), radius=16, fill=fill)
        draw.text((x + 34, y + 42), label, font=body_font, fill=(255, 255, 255))

    draw.rectangle((0, h - 118, w, h), fill=(255, 255, 255))
    for i, label in enumerate(["Venta", "Tickets", "Caja", "Admin"]):
        x = 118 + i * 235
        color = (17, 130, 99) if i == 0 else (91, 108, 128)
        draw.text((x, h - 76), label, font=small_font, fill=color)

    img.save(STORE / "lotterynet-pro-screenshot-1080x1920-24bit.png")


def draw_store_screenshot_results():
    w, h = 1080, 1920
    img = Image.new("RGB", (w, h), (244, 247, 251))
    draw = ImageDraw.Draw(img)
    title_font = font("arialbd.ttf", 62)
    h2_font = font("arialbd.ttf", 38)
    body_font = font("arial.ttf", 28)
    small_font = font("arialbd.ttf", 22)
    number_font = font("arialbd.ttf", 44)

    draw.rectangle((0, 0, w, 190), fill=(198, 137, 26))
    draw.text((56, 54), "Resultados", font=title_font, fill=(255, 248, 232))
    draw.text((58, 128), "LotteryNet Pro", font=body_font, fill=(255, 245, 218))

    draw.rounded_rectangle((56, 230, 1024, 370), radius=18, fill=(255, 255, 255), outline=(215, 226, 239), width=2)
    draw.text((88, 266), "Hoy", font=h2_font, fill=(7, 17, 31))
    draw.text((790, 266), "18:42", font=h2_font, fill=(23, 52, 92))
    draw.text((88, 320), "Resultados listos para verificar tickets", font=body_font, fill=(91, 108, 128))

    y = 420
    results = [
        ("Nacional", "25", "14", "90"),
        ("Leidsa", "407", "81", "33"),
        ("Real", "12", "18", "74"),
        ("La Primera", "74", "26", "05"),
        ("Loto Pool", "39", "11", "68"),
    ]
    for name, first, second, third in results:
        draw.rounded_rectangle((56, y, 1024, y + 168), radius=18, fill=(255, 255, 255), outline=(215, 226, 239), width=2)
        draw.text((90, y + 30), name, font=h2_font, fill=(7, 17, 31))
        x = 610
        for value, fill in [(first, (23, 52, 92)), (second, (17, 130, 99)), (third, (232, 111, 46))]:
            draw.rounded_rectangle((x, y + 42, x + 92, y + 112), radius=14, fill=fill)
            draw.text((x + 21, y + 53), value, font=number_font, fill=(255, 255, 255))
            x += 112
        draw.text((90, y + 100), "Sorteo registrado", font=body_font, fill=(91, 108, 128))
        y += 194

    draw.text((56, y + 18), "Ticket verificado", font=h2_font, fill=(7, 17, 31))
    y += 84
    draw.rounded_rectangle((56, y, 1024, y + 250), radius=18, fill=(255, 255, 255), outline=(215, 226, 239), width=2)
    draw.text((88, y + 36), "Serial 00018427", font=body_font, fill=(23, 52, 92))
    draw.text((88, y + 88), "Estado", font=body_font, fill=(91, 108, 128))
    draw.rounded_rectangle((214, y + 78, 412, y + 128), radius=12, fill=(228, 247, 240))
    draw.text((242, y + 88), "Premiado", font=small_font, fill=(17, 130, 99))
    draw.text((88, y + 158), "Monto a pagar", font=body_font, fill=(91, 108, 128))
    draw.text((700, y + 144), "RD$ 1,250", font=number_font, fill=(17, 130, 99))

    draw.rectangle((0, h - 118, w, h), fill=(255, 255, 255))
    for i, label in enumerate(["Venta", "Tickets", "Resultados", "Caja"]):
        x = 96 + i * 232
        color = (198, 137, 26) if i == 2 else (91, 108, 128)
        draw.text((x, h - 76), label, font=small_font, fill=color)

    img.save(STORE / "lotterynet-pro-screenshot-results-1080x1920-24bit.png")


draw_logo(1024, "lotterynet-pro-logo-1024.png")
draw_logo(512, "lotterynet-pro-logo-512.png")
draw_feature_graphic()
draw_store_screenshot()
draw_store_screenshot_results()
print("Generated store assets in marketing/store")

"""Render the small Markdown privacy policy without third-party dependencies."""
from html import escape
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent


def inline(text):
    text = escape(text)
    text = re.sub(r"\[([^\]]+)\]\((https://[^)]+)\)", r'<a href="\2">\1</a>', text)
    return re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)


blocks = []
in_list = False
for block in (ROOT / "PRIVACY.md").read_text(encoding="utf-8").split("\n\n"):
    block = block.strip()
    if not block:
        continue
    if block.startswith("# "):
        blocks.append(f"<h1>{inline(block[2:])}</h1>")
    elif block.startswith("## "):
        blocks.append(f"<h2>{inline(block[3:])}</h2>")
    elif block.startswith("- "):
        blocks.append("<ul>" + "".join(f"<li>{inline(line[2:])}</li>" for line in block.splitlines()) + "</ul>")
    else:
        blocks.append(f"<p>{inline(block)}</p>")

page = '''<!DOCTYPE html>
<html lang="en">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Privacy policy | Word for Word</title>
<style>
body { max-width: 760px; margin: 0 auto; padding: 32px 24px 64px;
       font: 17px/1.65 system-ui, sans-serif; color: #20212b; background: #fff; }
h1 { line-height: 1.2; font-size: 32px; } h2 { line-height: 1.3; margin-top: 36px; font-size: 23px; }
a { color: #4338ca; overflow-wrap: anywhere; } li { margin: 8px 0; }
</style>
<main>
''' + "\n".join(blocks) + "\n</main>\n</html>\n"
(ROOT / "docs/privacy.html").write_text(page, encoding="utf-8", newline="\n")

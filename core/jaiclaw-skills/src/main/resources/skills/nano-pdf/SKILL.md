---
name: nano-pdf
description: "Edit PDFs with natural-language instructions using the nano-pdf CLI. Use when asked to modify, edit, or update content in PDF files."
alwaysInclude: false
requiredBins: [nano-pdf]
platforms: [darwin, linux]
---

# PDF Editor (nano-pdf)

Use `nano-pdf` to apply edits to PDF pages using natural-language instructions.

## Install

```bash
# Via uv (recommended)
uv tool install nano-pdf

# Or pip
pip install nano-pdf
```

## Quick Start

```bash
# Edit a specific page
nano-pdf edit deck.pdf 1 "Change the title to 'Q3 Results' and fix the typo in the subtitle"

# Edit page 3
nano-pdf edit report.pdf 3 "Update the revenue figure to $2.5M"
```

## Notes

- Page numbers may be 0-based or 1-based depending on version; if results look off by one, retry with the other
- Always verify the output PDF before sending
- Works with any PDF that has extractable text layers

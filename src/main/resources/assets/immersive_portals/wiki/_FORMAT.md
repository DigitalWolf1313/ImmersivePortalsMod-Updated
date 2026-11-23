# In-game wiki Markdown format

Pages live in `wiki/<lang>/<slug>.md`. The `en_us` folder is the fallback for missing languages.
Images are referenced as `images/<name>.png`.
A file in the mod resources under `wiki/images/` is used first.
Otherwise the image is downloaded from the image repository and cached on disk.
`wiki/_index.json` lists categories and page order.

Front matter is a `---` block of `key: value` lines. String values are JSON-quoted.
Keys: title, category, order, description, source, updated, outdated, manual.
A page with `manual: true` is skipped by the converter so hand edits are kept.

Supported syntax: headings, paragraphs, `-` and `1.` lists with nesting, `**bold**`, `*italic*`,
`~~strike~~`, `inline code`, fenced code blocks with an optional language, `>` quotes,
GFM tables, `---` rules, images, and links.

Heading ids follow the VuePress rule: lowercase, runs of spaces and punctuation become `-`,
and repeated ids get `-1`, `-2`. A heading ending in `{#id}` overrides the computed id.

Links: `wiki:<slug>#<id>` goes to another page, `#<id>` stays on the page, other links are URLs.
A paragraph that contains only an image should be rendered as a block image.
An image whose path starts with `http` failed to download and is a remote fallback.

Containers: `:::details Title` and `:::tip Title` start a container and a line with `:::` ends it.
Containers do not nest.

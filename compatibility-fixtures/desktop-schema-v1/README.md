# Desktop portable schema v1

Baseline commit: `7690bb0936195a7558ae08af2ef93ef4b69f5adf` from the Glacier Notes desktop repository.

Portable records retain UUIDs for notebooks, notes, checklist items, labels, and image assets.
Notebook fields are name, optional color, timestamps, and sort order. Notes retain notebook ID,
text/checklist type, title, Markdown content, checklist, image IDs, pin/archive/color/labels/trash,
and timestamps. Cloud ownership and optimistic versions are deliberately not inserted into the
desktop export payload.

`full.glacier.json`, `notebook.glacier.json`, and `note.glacier.json` are schema-v1 envelopes
produced from the desktop transfer model. They exercise default-notebook metadata, checklist IDs,
labels, image data, Markdown image references, and each supported export scope.

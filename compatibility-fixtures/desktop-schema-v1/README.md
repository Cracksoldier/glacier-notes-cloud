# Desktop portable schema v1

Baseline commit: `7690bb0936195a7558ae08af2ef93ef4b69f5adf` from the Glacier Notes desktop repository.

Portable records retain UUIDs for notebooks, notes, checklist items, labels, and image assets.
Notebook fields are name, optional color, timestamps, and sort order. Notes retain notebook ID,
text/checklist type, title, Markdown content, checklist, image IDs, pin/archive/color/labels/trash,
and timestamps. Cloud ownership and optimistic versions are deliberately not inserted into the
desktop export payload.

Executable import/export fixtures are added in M9. This directory records the M0/M1 compatibility
contract so database identifiers do not require redesign first.


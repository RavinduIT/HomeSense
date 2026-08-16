# Document sources

LaTeX sources for the three project documents. The built PDFs are written to
`docs/` and are committed there.

| Source | Output |
|---|---|
| `report.tex` | `docs/HomeSense-Technical-Report.pdf` |

The demonstration script and the defence notes are built from sources kept
outside version control, since this repository is shared with the course
instructor. On a team member's machine they are built with
`build.ps1 -IncludeInternal`, or `make internal`.

`preamble.tex` holds the shared page layout, typography, table and listing
styles, and the title page, so the three documents remain consistent.

## Building

The documents are compiled with [Tectonic](https://tectonic-typesetting.github.io/),
a self-contained LaTeX engine that downloads only the packages a document
actually uses. A full TeX Live installation is not required.

```bash
# macOS / Linux
brew install tectonic          # or: cargo install tectonic
make

# Windows
winget install TectonicProject.Tectonic
.\build.ps1
```

If Tectonic is installed somewhere other than on `PATH`:

```powershell
.\build.ps1 -Tectonic C:\tools\tectonic\tectonic.exe
```

## Fonts

The documents set Times New Roman for body text and Consolas for code, both of
which are present on Windows and on any machine with Microsoft's fonts
installed. On a system without them, replace the two `\setmainfont` and
`\setmonofont` lines in `preamble.tex` with fonts that are available, for
example TeX Gyre Termes and TeX Gyre Cursor.

## Figures

Diagrams are drawn in TikZ within `report.tex` rather than imported as images,
so they scale cleanly, match the document's typography, and have no external
dependencies:

| Figure | Content |
|---|---|
| 1 | Sequence diagram tracing one toggle through the system |
| 2 | Aspect-ratio fitting of a plan within a canvas |
| 3 | Component arrangement and the fields each process writes |

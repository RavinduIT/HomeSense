# Builds the project documents to PDF.
#
# Requires Tectonic, a self-contained LaTeX engine that fetches only the
# packages a document actually uses:
#
#   cargo install tectonic
#   brew install tectonic
#   https://github.com/tectonic-typesetting/tectonic/releases
#
# Windows users without make can run build.ps1 instead.

TECTONIC ?= tectonic
OUT      := ..

.PHONY: all internal clean

all: $(OUT)/HomeSense-Technical-Report.pdf

$(OUT)/HomeSense-Technical-Report.pdf: report.tex preamble.tex
	$(TECTONIC) -X compile $<
	mv report.pdf $@

# Internal preparation material, kept out of version control because the
# repository is shared with the course instructor. Present only on a team
# member's machine.
internal:
	@test -f demonstration-script.tex && $(TECTONIC) -X compile demonstration-script.tex \
	  && mv demonstration-script.pdf $(OUT)/HomeSense-Demonstration-Script.pdf || true
	@test -f defence-notes.tex && $(TECTONIC) -X compile defence-notes.tex \
	  && mv defence-notes.pdf $(OUT)/HomeSense-Defence-Notes.pdf || true

clean:
	rm -f *.aux *.log *.out *.toc *.pdf
	rm -rf preview

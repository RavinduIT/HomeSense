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

.PHONY: all clean

all: $(OUT)/HomeSense-Technical-Report.pdf \
     $(OUT)/HomeSense-Demonstration-Script.pdf \
     $(OUT)/HomeSense-Defence-Notes.pdf

$(OUT)/HomeSense-Technical-Report.pdf: report.tex preamble.tex
	$(TECTONIC) -X compile $<
	mv report.pdf $@

$(OUT)/HomeSense-Demonstration-Script.pdf: demonstration-script.tex preamble.tex
	$(TECTONIC) -X compile $<
	mv demonstration-script.pdf $@

$(OUT)/HomeSense-Defence-Notes.pdf: defence-notes.tex preamble.tex
	$(TECTONIC) -X compile $<
	mv defence-notes.pdf $@

clean:
	rm -f *.aux *.log *.out *.toc *.pdf
	rm -rf preview

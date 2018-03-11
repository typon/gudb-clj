OUTPUT_JS = out.js
PLATFORM ?= latest-macos-x64
OUTPUT_EXE = gudb-$(PLATFORM).exe

build: $(OUTPUT_JS)

release: build $(OUTPUT_EXE)

run: $(OUTPUT_JS)
	node $(OUTPUT_JS)

$(OUTPUT_JS):
	npm install
	npm run build-cljs

$(OUTPUT_EXE):
	npm run build-binary -- --target=$(PLATFORM) $(OUTPUT_JS) --output=$(OUTPUT_EXE)

.PHONY: build run release

SRC_FILES = ./src/gudb/core.cljs
NPM_OUTPUT = node_modules
NPM_INPUT = package.json
SHADOW_CLJS_BUILD = shadow-cljs.edn
OUTPUT_JS = out.js
PLATFORM ?= latest-macos-x64
OUTPUT_EXE = gudb-$(PLATFORM).exe

build: $(OUTPUT_JS)

release: build $(OUTPUT_EXE)

run: $(OUTPUT_JS)
	node $(OUTPUT_JS)

clean:
	rm $(OUTPUT_JS) $(OUTPUT_EXE)

watch: $(SRC_FILES) $(NPM_OUTPUT) $(SHADOW_CLJS_BUILD)
	npm run watch-cljs

$(OUTPUT_JS): $(SRC_FILES) $(NPM_OUTPUT) $(SHADOW_CLJS_BUILD)
	npm run build-cljs

$(NPM_OUTPUT): $(NPM_INPUT)
	npm install

$(OUTPUT_EXE):
	npm run build-binary -- --target=$(PLATFORM) $(OUTPUT_JS) --output=$(OUTPUT_EXE)


.PHONY: build run release clean watch

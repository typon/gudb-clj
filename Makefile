SRC_FILES = ./src/gudb/core.cljs
NPM_OUTPUT = node_modules
NPM_INPUT = package.json
SHADOW_CLJS_BUILD = shadow-cljs.edn
OUTPUT_DEV_JS = out_dev.js
OUTPUT_REL_JS = out_rel.js
PLATFORM ?= latest-macos-x64
OUTPUT_EXE = gudb-$(PLATFORM).exe
LOG = gudb.log

build: $(OUTPUT_REL_JS)

release: build $(OUTPUT_EXE)

run: $(OUTPUT_REL_JS)
	node $(OUTPUT_REL_JS)

clean:
	rm $(OUTPUT_DEV_JS) $(OUTPUT_REL_JS) $(OUTPUT_EXE) $(LOG)

watch: $(SRC_FILES) $(NPM_OUTPUT) $(SHADOW_CLJS_BUILD)
	npm run watch-cljs

$(OUTPUT_REL_JS): $(SRC_FILES) $(NPM_OUTPUT) $(SHADOW_CLJS_BUILD)
	npm run build-cljs

$(NPM_OUTPUT): $(NPM_INPUT)
	npm install

$(OUTPUT_EXE):
	# npm run build-binary -- --target=$(PLATFORM) $(OUTPUT_REL_JS) --output=$(OUTPUT_EXE) -d 
	npm run build-binary -- --target=$(PLATFORM) --output=$(OUTPUT_EXE) 


.PHONY: build run release clean watch

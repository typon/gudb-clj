(ns gudb.source-widget
  (:require
   [clojure.string :as string]
   [cljs.cache :as cache]
   [gudb.utils :refer [r-el r-component read-file clamp]]
   [gudb.streams :refer [app-state app-state-view]]
   [gudb.colors :as colors]
   [cljs.pprint :refer [cl-format pprint]]
   [shrimp-log.core :as l]
   [instaparse.core :as insta :refer-macros [defparser]]
   [potok.core :as ptk]
   [beicon.core :as rx]
   ["blessed" :as blessed]
   ["react" :as react]
   ["react-blessed-contrib" :refer [createBlessedComponent]]
   ["react-blessed" :as react-blessed]
   ["prismjs" :as prism]
   ["cheerio" :as cheerio]
   ; ["highlightjs/styles/solarized_light.css" :as sl]
   [pylon.classes])
  (:use-macros [shrimp-log.macros :only [debug trace spy info warn]]
               [pylon.macros :only [defclass super]]))


(l/set-opts! :out-file :log-file
             :pretty-print true
             :log-level :debug)


(defn jsx->clj
  [x]
  (into {} (for [k (.keys js/Object x)] [k (aget x k)])))


(defonce screen-ref (atom nil))

(def tokenizer
  (insta/parser
   "<sentence> = token*
     <token> = valid | anything
     <valid> = #'\\w+'
     <anything> = #'[^\\w]+'"))

(defn get-window [curr-window line-sel window-type wh num-lines]
  (let [whh (.ceil js/Math (/ wh 2))
        [w-start w-end] curr-window]
    (case window-type
      :default (cond
                (< line-sel w-start) [line-sel (+ line-sel wh)]
                (> line-sel w-end) [(- line-sel wh) line-sel]
                :else curr-window)
      :center (cond
                (>= (+ line-sel whh) num-lines) [(dec (- num-lines wh)) (dec num-lines)]
                (< (- line-sel whh) 0) [0 wh]
                :else [(- line-sel whh) (+ line-sel whh)]
      ))))

(defn create-lineno-token [lineno num-lines]
  (let [total-width (count (str num-lines))
        fmt-str (str "~" total-width "d")
        lineno-str (cl-format nil fmt-str lineno)]
    [:lineno lineno-str :length (count lineno-str)]))

(defn extract-variables [variables text]
  (let [tokenized (tokenizer text)
        tokenized' (for [token tokenized]
                    (if (contains? variables token)
                      [:variable token]
                      [:text token]))
        partitioned (partition-by #(first %1) tokenized')
        collapsed (map (fn [tokens]
                          (case (first (first tokens))
                        :variable [:variable (second (first tokens))]
                        :text [:text (string/join (map #(second %1) tokens))])) partitioned)]
        collapsed))


(defn highlight-node
  ([variables type text]
   (let [tokens (extract-variables variables text)]
    (for [token tokens] (into token [:length (count (second token))]))))
  ([variables type text highlight-class]
   (let [tokens (extract-variables variables text)]
                    (for [token tokens]
                      [(first token) (str "{" (or (get colors/color-scheme-prism highlight-class) (:token.default colors/color-scheme-prism)) "-fg}" (second token) "{/}") :length (count (second token))]))))

(defn highlight-nodes [variables nodes]
  (let [tokenized (for [node nodes]
                    (case (.-type node)
                      "text" (highlight-node variables :plain (.-data node))
                      "tag" (highlight-node variables :highlighted (.-data (first (.-children node))) (keyword (string/replace (.. node -attribs -class) " " ".")))))
        flattened (apply concat tokenized)
        partitioned (partition-by #(first %1) flattened)]
        (map (fn [tokens]
              (case (first (first tokens))
                :variable [:variable (second (first tokens)) :length (nth (first tokens) 3)]
                :text [:text (string/join (map #(second %1) tokens)) :length (reduce + 0 (map #(nth %1 3) tokens))])) partitioned)))




(defn highlight-source-line [source-line variables]
  (let [;hl-text (.highlightAuto hljs text)
        pr-html (.highlight prism source-line (.. prism -languages -clike) "c")
        ; pr-lines (string/split-lines pr-html)
        $-line (.load cheerio pr-html)
        ]
    ;(for [$-line $-lines]
    (let [$ ($-line "*")
          contents (.contents $)
          entries (.entries js/Object contents) ; get all child elems
          entries' (filter #(re-matches #"[0-9]+" (first %1)) entries) ; drop all other keys, keep Nodes only
          body (second (nth entries' 1)) ; keep only the body node, which is the second entry
          nodes (.-children body)
          ]
      (highlight-nodes variables nodes))))


(defn code->elements [highlighted-tokens source-box border]
"Takes a source file and emits text widgets for each word"
  (for [[ix line-tokens] (map-indexed vector highlighted-tokens)]
    (let [line-el (blessed/box (clj->js {:height "0%+1" :top (+ ix (if border 1 0)) :wrap false :width "100%-2" :left 1}))
          word-lengths (map #(nth %1 3) line-tokens)
          word-lefts (reductions + 0 word-lengths)
          word-els (map (fn [token left length]
                     (case (first token)
                       :text (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true}))
                       :variable (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true :hoverText (str (second token) "0")})))) line-tokens word-lefts word-lengths)]

          (vector word-els))))


(defrecord Create-Source-Line-El [lineno]
  ptk/UpdateEvent
  (update [_ state]
    (let [
          source-line (get-in state [:source-box :source-lines lineno])
          line-tokens (highlight-source-line source-line (get-in state [:variables]))
          line-el (blessed/box (clj->js {:height "0%+1" :wrap false :width "100%-2" :left 1}))
          lineno-token (create-lineno-token lineno (get-in state [:source-box :num-source-lines]))
          space-token [:text " " :length 1]
          line-tokens' (cons lineno-token (cons space-token line-tokens))
          ;line-tokens' (concat '(lineno-token space-token) line-tokens)
          ; space-token
          word-lengths (map #(nth %1 3) line-tokens')
          word-lefts (reductions + 0 word-lengths) ; This number adds padding to the line.
          ;convert (clj->js line-tokens)]
          word-els (clj->js (map (fn [token left length]
                                    (case (first token)
                                     :lineno (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true :style colors/lineno-style}))
                                     :text (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true}))
                                     :variable (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true :hoverText (str (second token) "0")})))) line-tokens' word-lefts word-lengths))
          ]
      (.set line-el :lineno lineno)
      (trace "Created line" lineno)
      (-> state
          (assoc-in [:source-box :line-els lineno] line-el)
          (assoc-in [:source-box :lineno-els lineno] (first word-els))))))

(defrecord Line-Highlight [lineno type]
  ptk/EffectEvent
  (effect [_ state stream]
    (let [lineno-el (get-in state [:source-box :lineno-els lineno])]
      (case type
        :unselect (set! (.-style lineno-el) (clj->js colors/lineno-style))
        :select (set! (.-style lineno-el) (clj->js colors/hl-lineno-style))))))

(defrecord Source-Box-Scroll [lineno type window-hint]
  ptk/UpdateEvent
  (update [_ state]
    (let [new-sel (clamp lineno 0 (dec (get-in state [:source-box :num-source-lines])))]
      (-> state
          (assoc-in [:source-box :current-line] new-sel))))

  ptk/WatchEvent
  (watch [_ state stream]
    (let [line-sel (get-in state [:source-box :current-line])
          curr-window (get-in state [:source-box :window])
          wh (dec (get-in state [:source-box :window-height]))
          num-lines (get-in state [:source-box :num-source-lines])
          new-window (get-window curr-window line-sel window-hint wh num-lines)
          ]
      (if (= new-window curr-window)
        (rx/from-coll [(->Line-Highlight line-sel :select) (->Render-Screen)])
        (rx/just (->Source-Box-Display-Window new-window))))))


(defrecord Source-Box-Key-Press-Buff [key-obj]
  ptk/EffectEvent
  (effect [_ state stream]
    nil))

(rx/subscribe (as-> (ptk/input-stream app-state) $
               (rx/filter #(instance? Source-Box-Key-Press-Buff %) $)
               (rx/map #(:key-obj %) $) ; Extract the key-obj arg from the event
               (rx/merge (rx/buffer 2 1 $) $)) ; Remember the last two key presses
              ;; #(let [ev %]
              ;;    (js* "debugger;")
              ;;    (debug (clj->js %))))
              #(ptk/emit! app-state (->Source-Box-Key-Press %))) ; on-value func



(defrecord Source-Box-Key-Press [key-objs]
  ptk/WatchEvent
  (watch [_ state stream]
    (let [curr (get-in state [:source-box :current-line])
          num-lines (get-in state [:source-box :num-source-lines])
          wh (dec (get-in state [:source-box :window-height]))
          page-offset (* 1 wh)
          key (if (map? key-objs)
                (get-in key-objs ["full"])
                (vector (get (aget key-objs 0) "full") (get (aget key-objs 1) "full")))
          ]
      (case key
        "down" (rx/of (->Line-Highlight curr :unselect) (->Source-Box-Scroll (inc curr) :cursor :default))
        "up" (rx/from-coll [(->Line-Highlight curr :unselect) (->Source-Box-Scroll (dec curr) :cursor :default)])
        "S-g" (rx/from-coll [(->Line-Highlight curr :unselect) (->Source-Box-Scroll (dec num-lines) :cursor :default)])
        "C-f" (rx/from-coll [(->Line-Highlight curr :unselect) (->Source-Box-Scroll (+ curr page-offset) :cursor :center)])
        "C-u" (rx/from-coll [(->Line-Highlight curr :unselect) (->Source-Box-Scroll (- curr page-offset) :cursor :center)])
        ["g" "g"] (rx/from-coll [(->Line-Highlight curr :unselect) (->Source-Box-Scroll 0 :cursor :default)])
        rx/empty)))) ; Default case



(defrecord Source-Box-Render-Window []
  ptk/EffectEvent
  (effect [_ state stream]
    (let [source-box (get-in state [:elements :source-box])
          [start end] (get-in state [:source-box :window])
          lines-cache (get-in state [:source-box :line-els])
          ; _ (doseq [k (keys lines-cache)] (debug "Cache has " k " : " (str (cache/lookup lines-cache k))))
          ; _ (doseq [lineno (range start (+ end 1))] (debug "Cache " lineno " : " (str (cache/lookup lines-cache lineno))))
          lines-to-render (for [lineno (range start (+ end 1))] (cache/lookup lines-cache lineno))]
          ; _ (trace "Sart: " start)]
      ; (doseq [line-el (.-children source-box)] ; First remove all children
        ; (.remove source-box line-el))
      (set! (.-children source-box) (clj->js [])) ; First remove all children

      (doseq [line-el lines-to-render]
        (let [lineno (.get line-el :lineno)
              offset (+ 1 (- lineno start))]
          (set! (.-top line-el) (str "0%+" offset))))
      (doseq [line-el lines-to-render] ; Add all new children
        (.append source-box line-el))
      nil
      )))


(defrecord Source-Box-Ensure-Cache []
  ptk/UpdateEvent
  (update [_ state]
    (let [[start end] (get-in state [:source-box :window])
          lines-cache (get-in state [:source-box :line-els])
          linesnos-cache (get-in state [:source-box :lineno-els])
          ; Hit everything in our current window to ensure it doesnt get evicted.
          lines-cache-hot (reduce #(cache/hit %1 %2) lines-cache (range start (+ end 1)))
          linenos-cache-hot (reduce #(cache/hit %1 %2) linesnos-cache (range start (+ end 1)))
          ]
      (-> state
        (assoc-in [:source-box :line-els] lines-cache-hot)
        (assoc-in [:source-box :lineno-els] linenos-cache-hot))))

  ptk/WatchEvent
  (watch [_ state stream]
    (let [[start end] (get-in state [:source-box :window])
          lines-cache (get-in state [:source-box :line-els])
          ]
      (->> (rx/from-coll (range start (+ end 1)))
           (rx/filter #(not (cache/has? lines-cache %1)))
           (rx/flat-map #(rx/just (->Create-Source-Line-El %1)))))))

(defrecord Source-Box-Display-Window [lines-range]
  ptk/UpdateEvent
  (update [_ state]
    (let [end (min (second lines-range) (get-in state [:source-box :num-source-lines]))
          start (max 0 (first lines-range))]
      (assoc-in state [:source-box :window] [start end])))
  ptk/WatchEvent
  (watch [_ state stream]
    (let [curr (get-in state [:source-box :current-line])]
      (rx/concat
        (rx/just (->Source-Box-Ensure-Cache))
        (rx/just (->Source-Box-Render-Window))
        (rx/just (->Line-Highlight curr :select))
        (rx/just (->Render-Screen))))))




(defrecord Source-Box-Initialize []
  ptk/UpdateEvent
  (update [_ state]
    (let [source-box (get-in state [:elements :source-box])
          height (.-height source-box)
          vert-padding (get-in state [:source-box :vert-padding])
          window-height (- height vert-padding)
          ]
      (-> state
        (assoc-in [:source-box :height] height)
        (assoc-in [:source-box :window-height] window-height)
      ))))


(defrecord Register-Elem [el-name el-obj]
  ptk/UpdateEvent
  (update [_ state]
    (assoc-in state [:elements el-name] el-obj)))

(defrecord Set-Current-Source-Text [file-path]
  ptk/UpdateEvent
  (update [_ state]
    (let [source-text (read-file file-path)
          state' (assoc-in state [:source-box :source-lines] (string/split-lines source-text))]
      (assoc-in state' [:source-box :num-source-lines] (count (get-in state' [:source-box :source-lines]))))))

(defrecord Set-Active-Variables [vars]
  ptk/UpdateEvent
  (update [_ state]
    (assoc-in state [:variables] vars)))

(defrecord Render-Screen []
  ptk/EffectEvent
  (effect [_ state stream]
    (let [screen (get-in state [:elements :screen])]
      (.render screen))))

(def SourceBox
  (r-component "SourceBox"
               :componentDidMount (fn [] (ptk/emit! app-state (->Source-Box-Initialize)))
               :shouldComponentUpdate (fn [nextProps, nextState] false)
               :render (fn [props] (r-el "box" {
                                                :ref (fn [el] (ptk/emit! app-state (->Register-Elem :source-box el)))
                                                :key 0
                                                ; :left 0
                                                :width "50%"
                                                :height "50%"
                                                :style {:fg "red" :bg "magenta", :hover {:bg "black"}},
                                                :content "No source file loaded."
                                                :focused true,
                                                :keys true,
                                                :input true,
                                                ; :scrollable true,
                                                :wrap false,
                                                ; :scrollbar {"ch" "|"},
                                                :border {:type "line"}
                                                ; :content "Hello"

                                                :onKeypress (fn [_ key-obj] (ptk/emit! app-state (->Source-Box-Key-Press-Buff (js->clj key-obj))))
                                                ; :onScroll (fn [event] (dispatch :source-box-scroll event))
                                                ; :items ["hello" "there"]
                                                ; :hoverText "OYEEEEE"

                                                })
                         )))

(def App
  (r-component "App"
               :render (fn [props]
                          (r-el SourceBox (merge props {:key 10 :height "50%" :width "50%"})))))


(defn render [state]
  (do
    (trace "Rendering...........")
    (react-blessed/render (r-el App state) @screen-ref)))

(defn main! []
  (let [[screen] [(blessed/screen (clj->js {:smartCSR true "cursor.blink" true :log "blessedz.log" :autoPadding false}))]
        ]
    (debug "Starting GUDB!")
    (reset! screen-ref screen)
    (ptk/emit! app-state (->Register-Elem :screen @screen-ref))
    ;(swap! app-state assoc-in [:elements :screen] @screen-ref)
    (.key @screen-ref (clj->js ["q" "C-c"]) (fn [ch, key] (js/process.exit 0)))
    (.key @screen-ref (clj->js ["b"]) (fn [ch, key] (do
                                                      (ptk/emit! app-state (->Set-Active-Variables #{"i" "j" "test"}))
                                                      (ptk/emit! app-state (->Set-Current-Source-Text "/Users/typon/githubz/gudb/sample_program/large.c"))
                                                      (ptk/emit! app-state (->Source-Box-Display-Window [0 20]))
                                                      )))

                                                      ;())))
    ; (rx/on-value (rx/from-event @screen-ref "key") #(debug "key: " %1))
                                                      ;(dispatch :set-active-variables #{"i" "j" "test"})
                                                      ; (dispatch :set-current-source-text "/Users/typon/githubz/gudb/sample_program/simple.c")
                                                      ; (dispatch :source-box-initialize [0 50]))))
    (.key @screen-ref (clj->js ["l"]) (fn [ch, key] (debug (str "Width: " (.-width @screen-ref) "Hiegth: " (.-height @screen-ref)))))
    (.enableInput @screen-ref)
    (render @app-state-view)))

; Every time state changes, call render function again to redraw everything.
;; (add-watch app-state-view :redraw
;;            (fn [_ _ old-state new-state]
;;     ; Only re-render when old-state != new-state, ignoring the :elements
;;     ; because they get regenerated every time react renders. If we re-render
;;     ; then we get in infinite loop.
;;              (debug "State: " (get new-state :prefix))
;;              ; (debug "State: " (:elements new-state))
;;              (when-not
;;               (=
;;                (dissoc old-state :elements)
;;                (dissoc new-state :elements))
;;                (render new-state))))

(def state-change (rx/subscribe app-state
                       #(trace (str "State:\n"  %))
                       #(warn "on-error:" %)
                       #(info "on-end:")))

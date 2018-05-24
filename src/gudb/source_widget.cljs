(ns gudb.source-widget
  (:require
   [clojure.string :as string]
   [cljs.cache :as cache]
   [cljs.pprint :refer [cl-format pprint]]
   [shrimp-log.core :as l]
   [instaparse.core :as insta :refer-macros [defparser]]
   [potok.core :as ptk]
   [beicon.core :as rx]
   ["blessed" :as blessed]
   ["react" :as react]
   ["react-blessed" :as react-blessed]
   ["prismjs" :as prism]
   ["cheerio" :as cheerio]
   [gudb.utils :refer [r-el r-component read-file clamp jsx->clj file-exists?]]
   [gudb.streams :as strm :refer [app-state app-state-view]]
   [gudb.control :as ctrl]
   [gudb.styles :refer [styles]]
   [gudb.colors :as colors])

  (:use-macros [shrimp-log.macros :only [debug trace spy info warn]]
               [pylon.macros :only [defclass super]]))


(defonce TOP-OFFSET 0)
(defonce LEFT-OFFSET 0)
(defonce LINE-HORIZ-PADDING 2)




(def tokenizer
  (insta/parser
   "<sentence> = token*
     <token> = valid | anything
     <valid> = #'\\w+'
     <anything> = #'[^\\w]+'"))

(defn empty-cache [state]
  (as-> state $
   (assoc-in $ [:source-box :line-els] (cache/lru-cache-factory {} :threshold 50))
   (assoc-in $ [:source-box :lineno-els] (cache/lru-cache-factory {} :threshold 50))))


(defn remove-line-children [source-box]
  (let [children (.-children source-box)
        filtered (.filter children (fn [el] (not= (.-name el) "code-line")))]
    (set! (.-children source-box) filtered)
    source-box))


(defn get-window [curr-window line-sel window-type wh num-lines]
  (let [wh-lower-half (.floor js/Math (/ wh 2))
        wh-upper-half (if (even? wh) wh-lower-half (inc wh-lower-half))
        [w-start w-end] curr-window]
    (trace (str "line-sel: " line-sel))
    (trace (str "window-type: " window-type))
    (trace (str "Curr window: " curr-window))
    (trace (str "wh: " wh))
    (case window-type
      :default (cond
                (< line-sel w-start) [line-sel (+ line-sel wh)]
                (> line-sel w-end) [(- line-sel wh) line-sel]
                :else curr-window)
      :center (cond
                (>= (+ line-sel wh-upper-half) num-lines) [(dec (- num-lines wh)) (dec num-lines)]
                (< (- line-sel wh-lower-half) 0) [0 wh]
                :else [(- line-sel wh-lower-half) (+ line-sel wh-upper-half)]
      ))))

(defn create-lineno-token [lineno num-lines]
  (let [total-width (count (str num-lines))
        fmt-str (str "~" total-width "d")
        lineno-str (cl-format nil fmt-str (inc lineno))]
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
  (let [pr-html (.highlight prism source-line (.. prism -languages -clike) "c")
        $-line (.load cheerio pr-html)
        ]
    (let [$ ($-line "*")
          contents (.contents $)
          entries (.entries js/Object contents) ; get all child elems
          entries' (filter #(re-matches #"[0-9]+" (first %1)) entries) ; drop all other keys, keep Nodes only
          body (second (nth entries' 1)) ; keep only the body node, which is the second entry
          nodes (.-children body)
          ]
      (highlight-nodes variables nodes))))

(defrecord Create-Source-Line-El [lineno]
  ptk/UpdateEvent
  (update [_ state]
    (let [source-line (get-in state [:source-box :source-lines lineno])
          line-tokens (highlight-source-line source-line (get-in state [:variables]))
          line-el (blessed/box (clj->js {:height "0%+1" :wrap false :width (str "100%-" LINE-HORIZ-PADDING) :left LEFT-OFFSET :name "code-line"}))
          lineno-token (create-lineno-token lineno (get-in state [:source-box :num-source-lines]))
          space-token [:text " " :length 1]
          line-tokens' (cons lineno-token (cons space-token line-tokens))
          word-lengths (map #(nth %1 3) line-tokens')
          word-lefts (reductions + 0 word-lengths) ; This number adds padding to the line.
          word-els (clj->js (map (fn [token left length]
                                    (case (first token)
                                     :lineno (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true :style (get-in styles [:sbox :lineno :default])}))
                                     :text (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true}))
                                     :variable (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true :hoverText (str (second token) "0")})))) line-tokens' word-lefts word-lengths))
          ]
      (.set line-el :lineno lineno)
      (trace "Created line" lineno)
      (-> state
          (assoc-in [:source-box :line-els lineno] line-el)
          (assoc-in [:source-box :lineno-els lineno] (first word-els))))))

(defrecord Line-Highlight [type]
  ptk/EffectEvent
  (effect [_ state stream]
    (case type
      :stopped
        (let [prev (first @stopped-line!)
              curr (second @stopped-line!)
              prev-lineno-el (get-in state [:source-box :lineno-els prev])
              curr-lineno-el (get-in state [:source-box :lineno-els curr])]
          (trace (str "sel line: " @sel-line!))
          (trace (str "stopped line: " @stopped-line!))
          (trace (str "Prev stopped line: " prev))
          (trace (str "Current stopped line: " curr))
          (trace (str "Prev stopped lineel: " prev-lineno-el))
          (trace (str "Current stopped lineel: " curr-lineno-el))
          (cond
            (= prev nil) (set! (.-style curr-lineno-el) (clj->js (get-in styles [:sbox :lineno :stopped]))) ; Start of stream, prev is nil.
          :else (set! (.-style curr-lineno-el) (clj->js (get-in styles [:sbox :lineno :stopped]))))
          (when (and (not= nil prev-lineno-el) (not= prev (second @sel-line!)))
            (set! (.-style prev-lineno-el) (clj->js (get-in styles [:sbox :lineno :default])))))

      :clear-stopped
          (let [prev (first @stopped-line!)
                curr (second @stopped-line!)
                prev-lineno-el (get-in state [:source-box :lineno-els prev])
                curr-lineno-el (get-in state [:source-box :lineno-els curr])]
            ;; (debug (str "sel line: " @sel-line!))
            ;; (debug (str "stopped line: " @stopped-line!))
            ;; (debug (str "Prev stopped line: " prev))
            ;; (debug (str "Current stopped line: " curr))
            ;; (debug (str "Prev stopped lineel: " prev-lineno-el))
            ;; (debug (str "Current stopped lineel: " curr-lineno-el))

            (when-not (= curr-lineno-el nil)
              (set! (.-style curr-lineno-el) (clj->js (get-in styles [:sbox :lineno :default]))))
            (when-not (= prev-lineno-el nil)
              (set! (.-style prev-lineno-el) (clj->js (get-in styles [:sbox :lineno :default])))))
      :breakpoint
        (let [prev (first @stopped-line!)
              curr (second @stopped-line!)
              prev-lineno-el (get-in state [:source-box :lineno-els prev])
              curr-lineno-el (get-in state [:source-box :lineno-els curr])]
          (trace (str "sel line: " @sel-line!))
          (trace (str "stopped line: " @stopped-line!))
          (trace (str "Prev stopped line: " prev))
          (trace (str "Current stopped line: " curr))
          (trace (str "Prev stopped lineel: " prev-lineno-el))
          (trace (str "Current stopped lineel: " curr-lineno-el))
          (cond
            (= prev nil) (set! (.-style curr-lineno-el) (clj->js (get-in styles [:sbox :lineno :stopped]))) ; Start of stream, prev is nil.
          :else (set! (.-style curr-lineno-el) (clj->js (get-in styles [:sbox :lineno :stopped]))))
          (when (and (not= nil prev-lineno-el) (not= prev (second @sel-line!)))
            (set! (.-style prev-lineno-el) (clj->js (get-in styles [:sbox :lineno :default])))))


      ; :unselect (set! (.-style lineno-el) (clj->js colors/lineno-style))
      :select
        (let [prev-sel (first @sel-line!)
              curr-sel (second @sel-line!)
              stopped-line (second @stopped-line!)
              prev-lineno-el (get-in state [:source-box :lineno-els prev-sel])
              curr-lineno-el (get-in state [:source-box :lineno-els curr-sel])]
            (debug (str "stopped line: " stopped-line))
            (debug (str "sel line: " @sel-line!))
            (debug (str "Prev select line: " prev-sel))
            (debug (str "Current select line: " curr-sel))
            ; (debug (str "Prev select lineel: " prev-lineno-el))
            ; (debug (str "Current select lineel: " curr-lineno-el))

        (when (and (not= nil prev-lineno-el) (not= prev-sel stopped-line))
          (set! (.-style prev-lineno-el) (clj->js (get-in styles [:sbox :lineno :default]))))
        (when-not (= curr-sel stopped-line)
          (do
            (debug (str "Highlighting line: " curr-sel))
            (debug (str "Setting style: " (get-in styles [:sbox :lineno :selected])))
          (set! (.-style curr-lineno-el) (clj->js (get-in styles [:sbox :lineno :selected])))))))))

(def sel-line!
  (rx/to-atom
    (as-> app-state $
      (rx/map #(get-in % [:source-box :current-line]) $); Extract the current selected line
      (rx/dedupe $)
      (rx/concat (rx/just 0) $) ; Initialize by highlight line 0
      (rx/buffer 2 1 $)  ; Remember the last two current lines
      )))

(def stopped-line!
  (rx/to-atom
    (as-> app-state $
      (rx/map #(get-in % [:stopped-info :frame :line]) $); Extract the current selected line
      (rx/dedupe $)
      (rx/concat (rx/just nil) $) ; Initialize by highlight line 0
      (rx/buffer 2 1 $)  ; Remember the last two current lines
      )))

(defrecord Source-Box-Scroll [lineno type window-type]
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
          new-window (get-window curr-window line-sel window-type wh num-lines)
          ]
      (if (= new-window curr-window)
        (rx/of (->Line-Highlight :select) (strm/->Render-Screen))
        (rx/just (->Source-Box-Display-Window new-window))))))


(defrecord Source-Box-Key-Press-Buff [key-obj]
  ptk/EffectEvent
  (effect [_ state stream]
    nil))

(rx/subscribe (as-> (ptk/input-stream app-state) $
               (rx/filter #(instance? Source-Box-Key-Press-Buff %) $)
               (rx/map #(:key-obj %) $) ; Extract the key-obj arg from the event
               (rx/merge (rx/buffer 2 1 $) $)) ; Remember the last two key presses
              #(ptk/emit! app-state (->Source-Box-Key-Press %))) ; on-value func

(defrecord Source-Box-Key-Press [key-objs]
  ptk/WatchEvent
  (watch [_ state stream]
    (let [curr-line (get-in state [:source-box :current-line])
          curr-file (get-in state [:stopped-info :frame :fullname])
          num-lines (get-in state [:source-box :num-source-lines])
          wh (dec (get-in state [:source-box :window-height]))
          page-offset (* 1 wh)
          key (if (map? key-objs)
                (get-in key-objs ["full"])
                (vector (get (aget key-objs 0) "full") (get (aget key-objs 1) "full")))
          ]
      (case key
        ; Control
        "n" (rx/just (->Send-Cmd "-exec-next"))
        "s" (rx/just (->Send-Cmd "-exec-step"))
        "c" (rx/just (->Send-Cmd "-exec-continue"))
        "r" (rx/just (->Send-Cmd "-exec-run"))
        "f" (rx/just (->Send-Cmd "-exec-finish"))
        "u" (rx/just (->Send-Cmd "-exec-until"))
        "b" (rx/just (->Send-Cmd (str "-break-insert " curr-line)))

        ; Navigation
        ("down" "j") (rx/of (->Source-Box-Scroll (inc curr-line) :cursor :default))
        ("up" "k") (rx/just (->Source-Box-Scroll (dec curr-line) :cursor :default))
        "S-g" (rx/just (->Source-Box-Scroll (dec num-lines) :cursor :default))
        "C-f" (rx/just (->Source-Box-Scroll (+ curr-line page-offset) :cursor :center))
        "C-u" (rx/just (->Source-Box-Scroll (- curr-line page-offset) :cursor :center))
        ["g" "g"] (rx/just (->Source-Box-Scroll 0 :cursor :default))
        rx/empty)))) ; Default case

(defrecord Source-Box-Render-Window []
  ptk/EffectEvent
  (effect [_ state stream]
    (let [source-box (get-in state [:elements :source-box])
          [start end] (get-in state [:source-box :window])
          lines-cache (get-in state [:source-box :line-els])
          lines-to-render (for [lineno (range start (+ end 1))] (cache/lookup lines-cache lineno))]
      (trace "@@@@@@ Rendering window @@@@")
      (remove-line-children source-box)
      (doseq [line-el lines-to-render]
        (let [lineno (.get line-el :lineno)
              offset (+ TOP-OFFSET (- lineno start))]
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
    (let [curr-sel (get-in state [:source-box :current-line])
          curr-stopped (int (get-in state [:stopped-info :frame :line]))
          ]
      (rx/of
        (->Source-Box-Ensure-Cache)
        (->Source-Box-Render-Window)
        (->Line-Highlight :select)
        (strm/->Render-Screen)
        ))))

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

(defrecord Set-Label [text]
  ptk/EffectEvent
  (effect [_ state stream]
    (let [source-box (get-in state [:elements :source-box])]
      (trace "Setting label to: " text)
      (.setLabel source-box (clj->js {:side "right" :text text})))))


(defrecord Set-Current-Source-Text [file-path]
  ptk/UpdateEvent
  (update [_ state]
    (let [source-text (read-file file-path)]
      (as-> state $
        (empty-cache $)
        (assoc-in $ [:source-box :current-file] file-path)
        (assoc-in $ [:source-box :source-lines] (string/split-lines source-text))
        (assoc-in $ [:source-box :num-source-lines] (count (get-in $ [:source-box :source-lines]))))))

    ;; (do (debug "SETTING NEW SOURCE TEXT")
    ;; (let [source-text (read-file file-path)
    ;;       st'(as-> state $
    ;;            (empty-cache $)
    ;;            (assoc-in $ [:source-box :current-file] file-path)
    ;;            (assoc-in $ [:source-box :source-lines] (string/split-lines source-text))
    ;;            (assoc-in $ [:source-box :num-source-lines] (count (get-in $ [:source-box :source-lines])))
    ;;   )]
    ;;   (debug (str "NEW STATE: " st'))
    ;;   st')))

  ptk/WatchEvent
  (watch [_ state stream]
    (rx/of
     (->Set-Label (str "File: " file-path))
     (strm/->Render-Screen))))


(def SourceBox
  (r-component "SourceBox"
     :componentDidMount (fn [] (ptk/emit! app-state (->Source-Box-Initialize)))
     :shouldComponentUpdate (fn [nextProps, nextState] false)
     :render (fn [props]
       (r-el "box" {
          :ref (fn [el] (ptk/emit! app-state (strm/->Register-Elem :source-box el)))
          :key 0
          :width "50%"
          :height "50%"
          :style (get-in styles [:sbox :buffer])
          :content "{center}No source file loaded.{/}"
          :valign :middle
          :tags true,
          ; :focused true,
          :keys true,
          ; :hidden true,
          :input true,
          :wrap false,
          :border {:type "line"}
          :onKeypress (fn [_ key-obj] (ptk/emit! app-state (->Source-Box-Key-Press-Buff (js->clj key-obj))))
          }))))

; Control commands
(defrecord Handle-Prog-Stopped []
  ptk/WatchEvent
  (watch [_ state stream]
    (rx/just (->Line-Highlight :clear-stopped))))
    ;(do
      ;(debug "~~~~~~~~~~Stopped program execution")

(defrecord Set-Stopped-Info [stopped]
  ptk/UpdateEvent
  (update [_ state]
    (assoc-in state [:stopped-info] stopped))

  ptk/WatchEvent
  (watch [_ state stream]
    (case (get-in state [:stopped-info :reason])
      "exited-normally" (rx/just (->Handle-Prog-Stopped))
      (let [file (get-in state [:stopped-info :frame :fullname])]
        (case (file-exists? file)
          false (rx/empty)
          true (rx/just (->Handle-Bpt-Stopped)))))))


(defrecord Handle-Bpt-Stopped []
  ptk/WatchEvent
  (watch [_ state stream]
    (let [stopped-line (get-in state [:stopped-info :frame :line])
          curr-stopped-file (get-in state [:stopped-info :frame :fullname])
          curr-disp-file (get-in state [:source-box :current-file])
          ]
      (debug "###### HANDLE BPT STOPPED ########")
      (debug (str "current stopped file: " curr-stopped-file))
      (debug (str "current disp file: " curr-disp-file))
      (rx/of
       (strm/->Set-Active-Variables #{"i" "j" "test"})
       (if (= curr-disp-file curr-stopped-file)
         (rx/empty)
         (->Set-Current-Source-Text curr-stopped-file))
       (->Source-Box-Scroll stopped-line :cursor :center)
       (->Line-Highlight :stopped)
       ))))

(defrecord Send-Cmd [cmd]
  ptk/EffectEvent
  (effect [_ state stream]
    nil))

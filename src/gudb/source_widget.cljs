(ns gudb.source-widget
  (:require
   [clojure.string :as string]
   [cljs.cache :as cache]
   [gudb.utils :refer [r-el r-component]]
   [gudb.streams :refer [dispatch app-state transform]]
   [gudb.colors :refer [color-scheme-prism]]
   [cljs.pprint :refer [cl-format pprint]]
   [shrimp-log.core :as l]
   [instaparse.core :as insta :refer-macros [defparser]]
   ["fs" :as fs]
   ["blessed" :as blessed]
   ["react" :as react]
   ["react-blessed-contrib" :refer [createBlessedComponent]]
   ["react-blessed" :as react-blessed]
   ["prismjs" :as prism]
   ["highlightjs" :as hljs]
   ["cheerio" :as cheerio]
   ; ["highlightjs/styles/solarized_light.css" :as sl]
   [pylon.classes])
  (:use-macros [shrimp-log.macros :only [debug trace spy info]]
               [pylon.macros :only [defclass super]]))


(l/set-opts! :out-file :log-file
             :pretty-print true
             :log-level :debug)


(defn jsx->clj
  [x]
  (into {} (for [k (.keys js/Object x)] [k (aget x k)])))


(defonce screen-ref (atom nil))
;; (defonce app-state (atom {}))
(defonce cnt (atom 0))

(defn read-file [path]
  (let []
    (.readFileSync fs path "utf8")))


(def tokenizer
  (insta/parser
   "<sentence> = token*
     <token> = valid | anything
     <valid> = #'\\w+'
     <anything> = #'[^\\w]+'"))

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
                      [(first token) (str "{" (or (get color-scheme-prism highlight-class) (:token.default color-scheme-prism)) "-fg}" (second token) "{/}") :length (count (second token))]))))

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
        pr-lines (string/split-lines pr-html)
        $-lines (map #(.load cheerio %1) pr-lines)
        ]
    (for [$-line $-lines]
      (let [$ ($-line "*")
            contents (.contents $)
            entries (.entries js/Object contents) ; get all child elems
            entries' (filter #(re-matches #"[0-9]+" (first %1)) entries) ; drop all other keys, keep Nodes only
            body (second (nth entries' 1)) ; keep only the body node, which is the second entry
            nodes (.-children body)
            ]
        (highlight-nodes variables nodes)))))


(defn code->elements [highlighted-tokens source-box border]
"Takes a source file and emits text widgets for each word"
  (for [[ix line-tokens] (map-indexed vector highlighted-tokens)]
    ; (debug line-tokens ix)))
    (let [line-el (blessed/box (clj->js {:height "0%+1" :top (+ ix (if border 1 0)) :wrap false :width "100%-2" :left 1}))
          word-lengths (map #(nth %1 3) line-tokens)
          ; _ (debug word-lengths)
          word-lefts (reductions + 0 word-lengths)
          word-els (map (fn [token left length]
                     (case (first token)
                       :text (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true}))
                       :variable (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true :hoverText (str (second token) "0")})))) line-tokens word-lefts word-lengths)]

            ; words word-lengths word-lefts)]
    ;;       words (string/split (spy :trace line) #"(?=[ ])")
    ;;       word-lengths (map count words)
          (vector word-els))))


(defmethod transform :source-box-scroll
  [state input]
  (let [[offset always border] input
        source-box (get-in state [:elements :source-box])
        -offset (* -1 offset)
        source-box-height (.-height source-box)
        first-child (first (.-children source-box))
        first-child-top (.-top first-child)
        last-child (last (.-children source-box))
        last-child-top (.-top last-child)]
  (when (and (<= (+ first-child-top -offset) (if border 1 0)) (>= (+ last-child-top -offset) (- source-box-height (if border 2 1)))
            (doseq [child (.-children source-box)]
              (set! (.-top child) (+ (.-top child) -offset)))))
  (identity state)))

(defmethod transform :set-current-source-text
  [state file-path]
  (let [source-text (read-file file-path)]
    (assoc-in state [:source-box :source-lines] (string/split-lines source-text))))

(defmethod transform :set-active-variables
  [state variables]
    (assoc state :variables variables))

;; (defmethod transform :highlight-source-line
;;   [state input]
;;   (let [[line-no line-text] input
;;         variables (:variables state)
;;         cache (get-in state [:source-box :lines])
;;         ]
;;     ;(defn highlight-source-line [source-line-no source-line variables]
;;       (if (cache/has? cache line-no) (get cache line-no)
;;         (let [;hl-text (.highlightAuto hljs text)
;;               pr-html (.highlight prism source-line (.. prism -languages -clike) "c")
;;               pr-lines (string/split-lines pr-html)
;;               $-lines (map #(.load cheerio %1) pr-lines)
;;               highlighted-tokens (for [$-line $-lines]
;;                                   (let [$ ($-line "*")
;;                                         contents (.contents $)
;;                                         entries (.entries js/Object contents) ; get all child elems
;;                                         entries' (filter #(re-matches #"[0-9]+" (first %1)) entries) ; drop all other keys, keep Nodes only
;;                                         body (second (nth entries' 1)) ; keep only the body node, which is the second entry
;;                                         nodes (.-children body)
;;                                         ]
;;                                     (highlight-nodes variables nodes)))]
;;               highlighted-tokens))))

(defmethod transform :create-source-line-el
  [state lineno]
  (let [source-line (get-in state [:source-box :source-lines lineno])
        line-tokens (highlight-source-line source-line (get-in state [:variables]))
        line-el (blessed/box (clj->js {:height "0%+1" :top -1 :wrap false :width "100%-2" :left 1}))
        word-lengths (map #(nth %1 3) line-tokens)
                                        ; _ (debug word-lengths)
        word-lefts (reductions + 0 word-lengths)
        word-els (map (fn [token left length]
                        (case (first token)
                          :text (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true}))
                          :variable (blessed/text (clj->js {:content (second token) :left left :parent line-el :wrap false :width length :tags true :hoverText (str (second token) "0")})))) line-tokens word-lefts word-lengths)]
    (.set line-el :lineno lineno)
    ; (debug (str "lineno: " lineno))
    ; (debug line-el)
    (assoc-in state [:source-box :line-els lineno] line-el)))



(defmethod transform :source-box-ensure-cache
  [state window]
  (let [[start end] window]
    ; (doseq [lineno (range start end)] (debug lineno))))
    (debug "Ensuring start")
    (doseq [lineno (range start end)] (dispatch :create-source-line-el lineno))
    (debug "Ensuring end")
    (identity state)))


(defmethod transform :source-box-display-window
  [state window]
  (let [source-box (get-in state [:elements :source-box])
        [start end] window
        lines-cache (get-in state [:source-box :line-els])
        _ (debug lines-cache)
        ;lines-to-render "hello"]
        ; lines-to-render (for [lineno (range start end)] (cache/lookup lines-cache lineno))]
        ]
    ; (debug lines-to-render)
    (debug (cache/has? lines-cache 1))
    (identity state)))


(defmethod transform :source-box-initialize
  [state lines-range]
  (let [source-box (get-in state [:elements :source-box])
        border true
        [start end] lines-range
        ; (assoc-in state [:source-box :window] lines-range)
        ; highlighted-tokens (cat (map-indexed #(dispatch :highlight-source-line [%1 %2]) (subvec (get-in state [:source-box :source-lines]) start end)))
        ; word-els (code->elements highlighted-tokens source-box border)
        ]
    ;(debug (get-in state [:source-box :source-lines end]))
    (debug "Ensuring")
    (dispatch :source-box-ensure-cache lines-range)
    (debug "Diplaying")
    (dispatch :source-box-display-window lines-range)
    (set! (.-scroll source-box) (fn [offset always] ((dispatch :source-box-scroll [offset always border]))))
    (identity state)))

;; (defmethod transform :source-box-update
;;   [state _]
;;   (let [source-box (get-in state [:elements :source-box])
;;         line (blessed/box (clj->js {:height "0%+1" :parent source-box :top @cnt :wrap false}))
;;         txt (blessed/text (clj->js {:content (str "ree" @cnt) :left 0 :parent line :hoverText "REEZ" :wrap false :width 6}))
;;         ; txt2 (blessed/text (clj->js {:content "hello there sir what r  u doing hurr" :left 10 :parent line  :hoverText "RAYS" :wrap false}))
;;         txt2 (blessed/text (clj->js {:content "1234512345123451234512345123451234512345" :left 10 :parent line  :hoverText "RAYS" :wrap false :width 40}))
;;         ; line-text (str "ree" @cnt "      " "ray")
;;         ; box-content (.-content source-box)
;;         ; full-content (str box-content "\n" line-text)
;;         ; txt3 (blessed/text (clj->js {:content "ray" :hoverText "RAYS" :width "100%" :parent source-box}))
;;        ]
;;     ; (.pushItem source-box (blessed/text {"ree" "ss"}))
;;     ; (.pushItem source-box txt3)
;;     ;; (.focus source-box)
;;     ; (.setHover (aget (.-items source-box) 1) "OUEA")
;;     ; (.setContent source-box full-content)
;;     (set! (.-scroll source-box) (fn [offset always]
;;                                   (let [-offset (* -1 offset)
;;                                         source-box-height (.-height source-box)
;;                                         first-child (first (.-children source-box))
;;                                         first-child-top (.-top first-child)
;;                                         last-child (last (.-children source-box))
;;                                         last-child-top (.-top last-child)]
;;                                     (when (and (<= (+ first-child-top -offset) 0) (>= (+ last-child-top -offset) (- source-box-height 1))
;;                                       (doseq [child (.-children source-box)]
;;                                       (set! (.-top child) (+ (.-top child) -offset)))))
;;                                     ;; (when (and (<= (+ first-child-top -offset) 0) (>= (+ last-child-top -offset) (- source-box-height 1))
;;                                     ;;           (doseq [child (.-children source-box)]
;;                                     ;;             (set! (.-left child) (+ (.-top child) -offset)))))
;;                                     )))

;;     ; (set! (.-top source-box) "50%")
;;     (.render @screen-ref)
;;     (doseq [child (.-children (first (.-children source-box)))]
;;     (swap! cnt inc)

;;     (let [first-line-width (reduce + 0 (map #(.-width %) (.-children (first (.-children source-box)))))]
;;       (assoc-in state [:source-box-children 0 :width] first-line-width))))

(defmethod transform :source-box-keypress
  [state key-obj]
  (let [source-box (get-in state [:elements :source-box])
        offset (case (get key-obj "full")
                "right" 1
                "left" -1
                nil) ; Default case
        ]

    (let [source-box-width (.-width source-box)
            first-child (first (.-children source-box))
            first-child-width (get-in state [:source-box-children 0 :width])
            first-child-left (.-left first-child)
            first-child-right (+ first-child-left first-child-width)
          ]
        (when (and offset (> (+ first-child-right offset) source-box-width))
          (doseq [child (.-children source-box)]
            (set! (.-left child) (+ (.-left child) offset))
          ; (assoc-in state [:source-box-value] "REE")
            (.render @screen-ref)))
    (identity state))))



(defmethod transform :register-elem
  [state value]
  (assoc-in state [:elements (:el-name value)] (:el-obj value)))

(def SourceBox
  (r-component "SourceBox"
               ;; :componentDidMount (fn [] (this-as this
               ;;                             (.insertItem (.. this -refs -me) 0 (.text blessed #js {:content "TESTING"}))
               ;;                             (.insertItem (.. this -refs -me) 1 (.text blessed #js {:content "TESTING"}))
               ;;                             (reset! app-state {})
               ;;                             ; (.pushItem (.. this -refs -me) (blessed/text #js {:content "xx" :style {:fg "white" :bg "RED"}}))))
               ;;                             ))
               :shouldComponentUpdate (fn [nextProps, nextState] false)
               :render (fn [props] (r-el "box" {
                                                 :ref (fn [el] (dispatch :register-elem {:el-name :source-box :el-obj el}))
                                                :key 0
                                                ; :left 0
                                                :width "50%"
                                                :height "50%"
                                                :style {:fg "red" :bg "magenta", :hover {:bg "black"}},
                                                :content "No source file loaded."
                                                :keys true,
                                                :scrollable true,
                                                :wrap false,
                                                ; :scrollbar {"ch" "|"},
                                                :border {:type "line"}
                                                ; :content "Hello"

                                                ; :draggable true
                                                ; :onKeypress (fn [_ key-obj] (dispatch :source-box-keypress (js->clj key-obj)))
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
    (swap! app-state assoc-in [:elements :screen] @screen-ref)
    (.key @screen-ref (clj->js ["q" "C-c"]) (fn [ch, key] (js/process.exit 0)))
    (.key @screen-ref (clj->js ["b"]) (fn [ch, key] (do
                                                      (dispatch :set-active-variables #{"i" "j" "test"})
                                                      (dispatch :set-current-source-text "/Users/typon/githubz/gudb/sample_program/simple.c")
                                                      (dispatch :source-box-initialize [0 50]))))
    (.key @screen-ref (clj->js ["l"]) (fn [ch, key] (debug (str "Width: " (.-width @screen-ref) "Hiegth: " (.-height @screen-ref)))))
    (.enableInput @screen-ref)
    (render @app-state)))

; Every time state changes, call render function again to redraw everything.
(add-watch app-state :redraw
           (fn [_ _ old-state new-state]
    ; Only re-render when old-state != new-state, ignoring the :elements
    ; because they get regenerated every time react renders. If we re-render
    ; then we get in infinite loop.
             (when-not
              (=
               (dissoc old-state :elements)
               (dissoc new-state :elements))
               (render new-state))))

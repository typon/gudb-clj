(ns gudb.source-widget
  (:require
   [clojure.string :as string]
   [gudb.utils :refer [r-el r-component]]
   [gudb.streams :refer [dispatch app-state transform]]
   [cljs.pprint :refer [cl-format pprint]]
   [shrimp-log.core :as l]
   ["fs" :as fs]
   ["blessed" :as blessed]
   ["react" :as react]
   ["react-blessed-contrib" :refer [createBlessedComponent]]
   ["react-blessed" :as react-blessed]
   [pylon.classes])
  (:use-macros [shrimp-log.macros :only [debug trace spy]]
               [pylon.macros :only [defclass super]]))


(l/set-opts! :out-file :log-file
             :pretty-print true
             :log-level :debug)


(defonce screen-ref (atom nil))
;; (defonce app-state (atom {}))
(defonce cnt (atom 0))

(defn read-file [path]
  (let []
    (.readFileSync fs path "utf8")))

(defn renderer [coords]
  (let [self (js* "this")
        xi (.-xi coords)
        yi (.-yi coords)
        xl (.-xl coords)
        yl (.-yl coords)
        width (- xl xi)
        height (- yl yi)
        row-offset (atom 0) ; which row are we on?
        row-index (atom 0) ;
        last-left (atom 0) ; the left coord of the last element
        last-elem-newline? (atom false) ; the left coord of the last element
        ]
    (fn [el i]
      (let [last (.getLastCoords self i)]
        (set! (.-shrink el) true)
        (spy :trace (str "content: " (.-content el)))
        (if (nil? last)
          (do ; this is the first element we are rendering
            (set! (.. el -position -left) 0)
            (set! (.. el -position -top) 0))
          (let [last-xl (.-xl last)
                last-xl' (if @last-elem-newline? 0 last-xl)
                is-newline (= (.-content el) ":NL")
                last-left (- last-xl' xi)
                ; _ (swap! last-left #(+ % last-xl))
                new-left (if is-newline 0 last-left)]
            (reset! last-elem-newline? is-newline)
            (if is-newline
              (do
                (set! (.. el -hidden) true)
                (swap! row-offset inc)))
            ; (set! (.. el -position -left) (spy :trace new-left))
            ; (set! (.-left (.-position el)) (spy :trace new-left))
            (set! (.. el -position -left) (spy :trace new-left))
            (set! (.. el -position -top) (spy :trace @row-offset))))))))


(defn code->elements [code-raw-text source-box]
    (let [code-lines (string/split-lines code-raw-text)
          ; [words] [(mapcat #(string/split % #"(?=[ ])") code')]
          ]
      (for [[ix line] (map-indexed vector code-lines)]
        (let [line-el (blessed/box (clj->js {:height "0%+1" :top ix :wrap false :parent source-box}))
              words (string/split (spy :trace line) #"(?=[ ])")
              word-lengths (map count words)
              word-lefts (reductions + 0 word-lengths)
              word-els (map #(blessed/text (clj->js {:content %1 :left %3 :parent line-el :hoverText (str %1 "0") :wrap false :width %2}))  words word-lengths word-lefts)
                         ;; (debug (str "word: " word))
                         ;; (debug (str "wordlenth: " word-length))
                          ;; (debug (str "wordleft: " word-left)))
                          ]

          (vector word-els)))))

(defmethod transform :source-box-update
  [state _]
  (let [source-box (get-in state [:elements :source-box])
        word-els (code->elements (read-file "/Users/typon/githubz/gudb/sample_program/simple.c") source-box)
        ]
    ; (debug word-els)
    ; (debug word-els)
    ; (doall word-els)
    (set! (.-scroll source-box) (fn [offset always]
                                  (let [-offset (* -1 offset)
                                        source-box-height (.-height source-box)
                                        first-child (first (.-children source-box))
                                        first-child-top (.-top first-child)
                                        last-child (last (.-children source-box))
                                        last-child-top (.-top last-child)]
                                    (when (and (<= (+ first-child-top -offset) 0) (>= (+ last-child-top -offset) (- source-box-height 1))
                                      (doseq [child (.-children source-box)]
                                      (set! (.-top child) (+ (.-top child) -offset)))))
                                    )))

    ; (.render @screen-ref)
    (assoc-in state [:source-box-value :word-els] word-els)))

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
;;     ; (debug "Sourcebox: " (.-content source-box))
;;     (set! (.-scroll source-box) (fn [offset always]
;;                                   (let [-offset (* -1 offset)
;;                                         source-box-height (.-height source-box)
;;                                         first-child (first (.-children source-box))
;;                                         first-child-top (.-top first-child)
;;                                         last-child (last (.-children source-box))
;;                                         last-child-top (.-top last-child)]
;;                                     (debug "Scrolled: " offset )
;;                                     (when (and (<= (+ first-child-top -offset) 0) (>= (+ last-child-top -offset) (- source-box-height 1))
;;                                       (doseq [child (.-children source-box)]
;;                                       (set! (.-top child) (+ (.-top child) -offset)))))
;;                                     ;; (when (and (<= (+ first-child-top -offset) 0) (>= (+ last-child-top -offset) (- source-box-height 1))
;;                                     ;;           (doseq [child (.-children source-box)]
;;                                     ;;             (set! (.-left child) (+ (.-top child) -offset)))))
;;                                     )))

;;     ; (set! (.-top source-box) "50%")
;;     (debug "children: " (count (.-children source-box)))
;;     (.render @screen-ref)
;;     (doseq [child (.-children (first (.-children source-box)))]
;;       ; (debug (.dir js/console child))
;;       (debug (.-type child))
;;       (debug "width: " (.-width child)))
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
        (debug "First child left " first-child-left )
        (debug "First child width " first-child-width )
        (debug "First child right " first-child-right )
        (debug "Source box width " source-box-width)
        (when (and offset (> (+ first-child-right offset) source-box-width))
          (doseq [child (.-children source-box)]
            (debug "Scrolled-h " offset)
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
               :render (fn [props] (r-el "box" {
                                                 :ref (fn [el] (dispatch :register-elem {:el-name :source-box :el-obj el}))
                                                :key 0
                                                ; :left 0
                                                :width "0%+20"
                                                :height "0%+10"
                                                :style {:fg "red" :bg "magenta", :hover {:bg "black"}},
                                                ; :search true,
                                                ; :mouse true,
                                                ; :input true,
                                                :keys true,
                                                ; :vi true,
                                                :scrollable true,
                                                :wrap false,
                                                ; :scrollbar {"ch" "|"},
                                                ; :border {:type "line"}
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
    (.key @screen-ref (clj->js ["b"]) (fn [ch, key] (dispatch :source-box-update nil)))
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

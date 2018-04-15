(ns gudb.gdb
  (:require
   [clojure.string :as string]
   [cljs.core.async :refer [chan <! >! timeout pub sub unsub unsub-all put! sliding-buffer]]
   [cljs.pprint :refer [pprint]]
   [com.rpl.specter :as sp]
   [gudb.utils :refer [throttle]]
   [gudb.streams :refer [dispatch]]
   [gudb.gdb-mi-parser :refer [gdb-output-parser transformer]]
   [gudb.gdb-msgs :refer [handle-gdb-result gdb-cmds-state]]
   ["child_process" :as child_process :refer [spawn]]
   ["node-pty" :as pty])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))


; Node builtins
(defonce process (js* "process"))

; Singletons
(defonce gdb-proc (atom nil))

; Publications
(defonce gdb-chan (chan))
(defonce gdb-publication
  (pub gdb-chan #(:topic %)))

; Subscribers
(def xform-gdb-input
  (comp
   (map #(:cmd %))
   (map string/trim)
   (remove string/blank?)))

;; (defonce gdb-cmds-sub (chan (sliding-buffer 1) xform-gdb-input))
;; (defonce gdb-cmds-sub-throttled (throttle gdb-cmds-sub 2000))

(defonce gdb-cmds-sub (chan 1 xform-gdb-input))

(sub gdb-publication :cmd-sent gdb-cmds-sub)

; Go-loops for handling input to gdb
(go-loop []
  (let [[cmd] [(<! gdb-cmds-sub)]]
    (trace (str "Sending cmd to gdb: " cmd))
    (reset! gdb-cmds-state cmd)
    (.write (.-stdin @gdb-proc) (str cmd "\r\n"))
    (recur)))


(def xform-gdb-output
  (comp
   (mapcat (fn [msg] (string/split (:text (spy :trace msg)) "\n")))
   ; (mapcat (fn [msg] (string/split msg #"(?=\^|\*|\&|=|~)")))
   (remove string/blank?)))

(defonce gdb-stdout-sub (chan 1 xform-gdb-output))
(sub gdb-publication :gdb-output-received gdb-stdout-sub)

; Go-loops for handling messages received from gdb
(go-loop []
  (let [[text] [(<! gdb-stdout-sub)]
        [parse-tree] [(gdb-output-parser (spy :trace text))]
        [transformed] [(transformer parse-tree)]
        [record-type] [(sp/select-one [sp/FIRST sp/FIRST] (spy :trace transformed))]
       ]
        ; [record-type] [(spy :debug parse-tree)]]
        ; [result] [(handle-gdb-output parse-tree)]
    ; (debug (str (sp/select-one [sp/FIRST sp/ALL string?] parse-tree)))
    ; (debug (str (sp/select-one [sp/FIRST sp/FIRST] parse-tree)))
    ; (dispatch :target-output-received {:thread 0 :text text})
    ; (debug (str "Tree: " parse-tree))
    ; (debug (str (nth (nth parse-tree 0) 1)))
    (handle-gdb-result record-type transformed parse-tree)
    (trace text)
    (recur)))


(def xform-prog-output
  (comp
   (drop 1)
   (map #(:text %))
   (mapcat string/split-lines)
   (remove (fn [text] (not= nil (re-find #"^&\"warning: GDB:" text)))) ; Supress warnings
   ))
   ; (map (fn [msg] (update-in msg [:text] #(sp/setval (sp/regex-nav #"\r\n$|\n$") "" %)))) ; Remove last newline
   ; (mapcat (fn [msg] (update-in msg [:text] #(sp/setval (sp/regex-nav #"\r\n$|\n$") "" %)))) ; Remove last newline
   ; (map (fn [msg] (debug msg)))))


(defonce prog-stdout-sub (chan 1 xform-prog-output))
(sub gdb-publication :prog-output-received prog-stdout-sub)


; Go-loop for handling messages received from program output
(go-loop []
  (let [[text] [(<! prog-stdout-sub)]]
    (debug (str "Received output from program: " text))
    (dispatch :target-output-received {:thread 0 :text text})
    (recur)))


(defn init-gdb []
  (let [[init-cmds] [(vector "set confirm off" "ads")]]
    (debug (str "Sending!: " init-cmds))
    ; (for [cmd init-cmds]
    (go (>! gdb-chan {:topic :cmd-sent :cmd (nth init-cmds 0)}))))



(defn spawn-pseudo-tty []
  (.spawn pty "bash" ;; the FD of the pseudo-terminal is stored in the protected memeber variable '_pty'
          (clj->js [])
          (clj->js {"name" "xterm"
                    "cols" 80
                    "rows" 30
                    "cwd" (.. process -env -HOME)
                    "env" (.. process -env)})))

(defn load-gdb [executable-path]
  (let [[p-tty] [(spawn-pseudo-tty)]
        ; [gdb-args] [(clj->js ["-i=mi2",  (spy :info (str "-tty=" (.-_pty p-tty))), executable-path])]
        [gdb-args] [(clj->js ["-i=mi",  (str "-tty=" (.-_pty p-tty)), executable-path])]
        [gdb-proc'] [(spawn "gdb" gdb-args)]
        [callback] [(.. gdb-proc -stdout)]]
    (reset! gdb-proc gdb-proc')
    (.on (.-stdout gdb-proc') "data" (fn [text] (go (>! gdb-chan {:topic :gdb-output-received :text (str text)}))))
    (.on (.-stderr gdb-proc') "data" (fn [msg] (debug (str "ERROR FROM GDB:\n" msg))))
    ; (.on p-tty "data" (fn [text] (dispatch :target-output-received {:thread 0 :text text})))))
    (.on p-tty "data" (fn [text] (go (>! gdb-chan {:topic :prog-output-received :text text}))))
    (init-gdb)))


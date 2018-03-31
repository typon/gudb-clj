(ns gudb.gdb
  (:require
   [clojure.string :as string]
   [cljs.core.async :refer [chan <! >! timeout pub sub unsub unsub-all put!]]
   [cljs.pprint :refer [pprint]]
   [com.rpl.specter :as sp]
   [gudb.streams :refer [dispatch]]
   [gudb.gdb-mi-parser :refer [gdb-output-parser transformer]]
   ["child_process" :as child_process :refer [spawn]]
   ["node-pty" :as pty]
   [clojure.string :as str])
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
(defonce gdb-cmds-sub (chan))
(sub gdb-publication :cmd-sent gdb-cmds-sub)

; Go-loops for handling input to gdb
(go-loop []
  (let [{:keys [cmd]} (<! gdb-cmds-sub)]
    (debug (str "Sending cmd to gdb: " cmd))
    (.write (.-stdin @gdb-proc) (str cmd "\n"))
    (recur)))


(def xform-gdb-output
  (comp
   (mapcat (fn [msg] (string/split (:text (spy :trace msg)) "\n")))
   (mapcat (fn [msg] (string/split msg #"(?=\^|\*|\&|=|~)")))
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
    (case record-type
      ; :ASYNC_RECORD (dispatch :history-box-append (sp/select-one [sp/FIRST sp/ALL string?] parse-tree))
      :ASYNC_RECORD ()
      :RESULT_RECORD ()
      :STREAM_RECORD (dispatch :history-box-append (str (sp/select-one [sp/FIRST 1] transformed)))
      :GDB_INTERNAL_RECORD ()
      ; (debug (str "UNCAUGHT: " (sp/select-one [sp/FIRST 1] transformed)))
      (trace (str "UNCAUGHT: " parse-tree))
    )
    ; (debug (str (sp/select-one [sp/FIRST sp/ALL string?] parse-tree)))
    ; (debug (str (sp/select-one [sp/FIRST sp/FIRST] parse-tree)))
    ; (dispatch :target-output-received {:thread 0 :text text})
    ; (debug (str "Tree: " parse-tree))
    ; (debug (str (nth (nth parse-tree 0) 1)))
    ; (debug msg)
    (recur)))


(def xform-prog-output
  (comp
   (drop 1)
   (remove (fn [msg] (not= nil (re-find #"^&\"warning: GDB:" (:text msg)))))
   (map (fn [msg] (update-in msg [:text] #(sp/setval (sp/regex-nav #"\r\n$|\n$") "" (spy :info %))))))) ; Remove last newline


(defonce prog-stdout-sub (chan 10 xform-prog-output))
(sub gdb-publication :prog-output-received prog-stdout-sub)


; Go-loop for handling messages received from program output
(go-loop []
  (let [{:keys [thread text]} (<! prog-stdout-sub)]
    (debug (str "Received output from program: " text))
    (dispatch :target-output-received {:thread 0 :text text})
    (recur)))


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
        [gdb-args] [(clj->js ["-i=mi2",  (str "-tty=" (.-_pty p-tty)), executable-path])]
        [gdb-proc'] [(spawn "gdb" gdb-args)]
        [callback] [(.. gdb-proc -stdout)]]
    (reset! gdb-proc gdb-proc')
    (.on (.-stdout gdb-proc') "data" (fn [text] (go (>! gdb-chan {:topic :gdb-output-received :text (str text)}))))
    (.on (.-stderr gdb-proc') "data" (fn [msg] (debug (str "ERROR FROM GDB:\n" msg))))
    ; (.on p-tty "data" (fn [text] (dispatch :target-output-received {:thread 0 :text text})))))
    (.on p-tty "data" (fn [text] (go (>! gdb-chan {:topic :prog-output-received :text (spy :info text) :thread 0}))))))


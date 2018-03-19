(ns gudb.gdb
  (:require 
    [cljs.core.async :refer [chan <! >! timeout pub sub unsub unsub-all]]
    ["child_process" :as child_process :refer [spawn]]
    ["pty.js" :as pty])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:use-macros [shrimp-log.macros :only [trace debug spy]]))

; Node builtins
(defonce process (js* "process"))

; Singletons
(defonce gdb-proc (atom nil))

; Publications
(defonce gdb-chan (chan))
(defonce gdb-publication
  (pub gdb-chan #(:topic %)))

; Subscribers
(def gdb-cmds-sub (chan))
(sub gdb-publication :cmd-sent gdb-cmds-sub)


; Go-loops for handling subscriptions
(go-loop []
  (let [{:keys [cmd]} (<! gdb-cmds-sub)]
    (trace (str "Sending cmd to gdb: " cmd))
    (.write (.-stdin @gdb-proc) (str cmd "\n"))
    (recur)))


(defn spawn-pseudo-tty []
  (.spawn pty "bash" 
             (clj->js [])
             (clj->js {"name" "xterm"
              "cols" 80
              "rows" 30
              "cwd" (.. process -env -HOME)
              "env" (.. process -env)})))


(defn load-gdb [executable-path]
  (let [
    [p-tty] [(spawn-pseudo-tty)]
    [gdb-args] [(clj->js ["-i=mi2", (spy :debug (str "-tty=" (.-pty p-tty))), executable-path])]
    [gdb-proc'] [(spawn "gdb" gdb-args)]
    [callback] [(.. gdb-proc -stdout)]
   ]
    (reset! gdb-proc gdb-proc')
    (.on (.-stdout gdb-proc') "data" (fn [msg] (debug (str "STDOUT FROM GDB:\n" msg))))
    (.on (.-stderr gdb-proc') "data" (fn [msg] (debug (str "ERROR FROM GDB:\n" msg))))
    (.on p-tty "data" (fn [msg] (debug (str "MESSAGE FROM PTTY:\n" msg))))))


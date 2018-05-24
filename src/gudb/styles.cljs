(ns gudb.styles)


(def solarized {
  :base03 "#002b36",
  :base02 "#073642",
  :base0  "#839496",
  :base00 "#657b83",
  :base01 "#586e75",
  :base1  "#93a1a1",
  :base2 	"#eee8d5",
  :base3 	"#fdf6e3",
  :yellow "#af8700",
  :magenta "#d33682",
  :red "#dc322f",
  :orange "#cb4b16",
  :violet "#6c71c4",
  :blue "#268bd2",
  :cyan "#2aa198",
  :green "#859900",
})


(defonce styles {
    :screen {
      :bg (:base3 solarized)
    }
    :selected {
        :bg (:base2 solarized)
        :fg (:base01 solarized)
    }
    :label {
        :bg (:base3 solarized)
        :fg (:orange solarized)
    }
    :divider {
        :bg (:base3 solarized)
        :fg (:base2 solarized)
    }
    :border {
        :fg (:base2 solarized)
        :bg (:base3 solarized)
    },
    :focusedBorder {
        :fg (:red solarized)
        :bg (:base3 solarized)
    },
    :sbox {
      :buffer {
          :bg (:base3 solarized)
          :fg (:base00 solarized)
          :hover {:bg (:base3 solarized)}
      },
      :gutter {
          :bg (:base2 solarized)
          :fg (:base01 solarized)
      },
      :lineno {
        :default {
          :bg (:base01 solarized)
        },
        :stopped {
          :bg (:orange solarized)
        },
        :selected {
          :bg (:cyan solarized)
        },
      }
    },
    :scrollbar {
        :bg (:base2 solarized)
        :fg (:base2 solarized)
    },
    :outputBox {
        :text {
            :bg (:base3 solarized)
            :fg (:base00 solarized)
        },
        :bg (:base3 solarized)
        :fg (:base01 solarized)
    },
    :historyBox {
        :text {
            :bg (:base3 solarized)
            :fg (:base00 solarized)
        },
        :bg (:base3 solarized)
        :fg (:base01 solarized)
    },
    :input-box {
        :text {
            :bg (:base3 solarized)
            :fg (:base00 solarized)
        },
        :bg (:base3 solarized)
        :fg (:base00 solarized)
    },
    :helpMessage {
        :text {
            :bg (:base2 solarized)
            :fg (:base00 solarized)
        },
        :border {
            :bg (:base01 solarized)
            :fg (:base1 solarized)
        },
        :label {
            :bg (:base01 solarized)
            :fg (:base3 solarized)
        },
        :bg (:base2 solarized)
        :fg (:base00 solarized)
    }
})

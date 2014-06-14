(ns chessground.ctrl
  "Changes to state.
   Each function returns a new state
   and a function taking an $app and channels,
   and mutating the dom"
  (:require [jayq.core :as jq :refer [$]]
            [chessground.common :as common :refer [pp]]
            [chessground.data :as data]
            [chessground.chess :as chess]))

(defn- callback [function & args]
  "Call a user supplied callback function, if any"
  (when function (apply function (map clj->js args))))

(defn- noop [] nil)

(defn- $square [$app key]
  ($ (str ".square[data-key=" key "]") $app))

(defn- show-selected [$app selected]
  (jq/remove-class ($ :.square.selected $app) :selected)
  (when selected (jq/add-class ($square $app selected) :selected)))

(defn- show-dests [$app dests]
  (doseq [square ($ :.square $app)]
    (if (common/set-contains? dests (.getAttribute square "data-key"))
      (-> square .-classList (.add "dest"))
      (-> square .-classList (.remove "dest")))))

(defn move-start [state orig]
  "A move has been started, either by clicking on a piece, or dragging it"
  (let [new-state (assoc state :selected orig)]
    [new-state
     (fn [$app chans]
       (show-selected $app orig)
       (show-dests $app (data/dests-of state orig)))]))

(defn move-piece [state [orig dest]]
  (or (when (data/can-move? state orig dest)
        (when-let [new-chess (chess/move-piece (:chess state) orig dest)]
          (let [new-state (-> state
                              (assoc :chess new-chess)
                              (dissoc :selected)
                              (assoc-in [:movable :dests] nil))]
            (callback (-> state :movable :events :after) orig dest new-chess)
            [new-state
             (fn [$app chans]
               (let [$orig ($square $app orig)
                     $dest ($square $app dest)
                     $piece ($ :.piece $orig)]
                 (show-selected $app nil)
                 (show-dests $app nil)
                 (.remove ($ :.piece $dest))
                 (.appendTo $piece $dest)))])))
      (if (= orig dest)
        (let [new-state (dissoc state :selected)]
          [new-state
           (fn [$app chans]
             (show-selected $app nil))])
        (let [new-state (assoc state :selected dest)]
          [new-state
           (fn [$app chans]
             (show-selected $app dest))]))))

(defn select-square [state key]
  (if-let [orig (:selected state)]
    (move-piece state [orig key])
    (if (chess/get-piece (:chess state) key)
      (move-start state key)
      [state noop])))
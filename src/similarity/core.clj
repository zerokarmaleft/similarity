(ns similarity.core
  (:require [clojure.set :as set]))

(defn sim-jaccard
  [s t]
  "Returns the similarity of sets as a relative size of their intersection."
  (/ (count (set/intersection s t))
     (count (set/union s t))))

(ns similarity.core
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str])
  (:use swank.core))

(def U  #{\a \b \c \d \e})
(def S1 #{\a \d})
(def S2 #{\c})
(def S3 #{\b \d \e})
(def S4 #{\a \c \d})
(def docs [S1 S2 S3 S4])

(defn sim-jaccard
  "Returns the similarity of sets as a relative size of their intersection."
  ([s t]
     (/ (count (set/intersection s t))
        (count (set/union s t))))
  ([s t & args]
     (let [sets (conj (conj args t) s)]
       (/ (count (apply set/intersection sets))
          (count (apply set/union sets))))))

(comment ; Exercise 3.1.1
  (sim-jaccard #{1 2 3 4} #{1 2 3 4})
  (sim-jaccard #{1 2 3 4} #{2 3 5 7})
  (sim-jaccard #{1 2 3 4} #{2 4 6})
  (sim-jaccard #{2 3 5 7} #{2 3 5 7})
  (sim-jaccard #{2 3 5 7} #{2 4 6})
  (sim-jaccard #{2 4 6} #{2 4 6}))

(defn shingles
  [k s]
  "Returns the set of k-shingles that appear one or more times in string s."
  (->> (str/replace s #"\s+" " ")
       (partition k 1 s)
       (map #(apply str %))
       (apply sorted-set)))

(comment ; Example 3.3
  (shingles 2 "abcdabd"))

(comment ; Example 3.4
  (let [D1 (shingles 9 "The plane was ready for touch down")
        D2 (shingles 9 "The quarterback scored a touchdown")]
    (sim-jaccard D1 D2))))

(comment ; Exercise 3.2 1
    (let [D "The most effective way to represent documents as sets, for the purpose of identifying lexically similar documents is to construct from the document the set of short strings that appear within it."]
      (take 10 (shingles 3 D))))

(defn characteristics
  [coll]
  "Builds a naively dense characteristic matrix from a collection of documents.
  Each column represents a bit-vector representing if a particular
  document's k-shingles is part of the universal k-shingle set."
  (let [universe (apply set/union coll)]
    (vec (map (fn [s]
                (vec (map #(contains? s %) universe)))
              coll))))
;; (characteristics [S1 S2 S3 S4])
(defn count-cols [C]
  (count C))

(defn count-rows [C]
  (reduce max (map count C)))
(def C (characteristics docs))

(defn char->row [c] (- (int c) 97))
(defn hash-1 [x] (mod (+ x 1) 5))
(defn hash-2 [x] (mod (+ (* x 3) 1) 5))

(defn transpose
  [C]
  (partition (count C) (apply interleave C)))

(defn merge-vectors
  [v1 v2]
  (map #(map min %1 %2) v1 v2))

(defn signatures
  [doc-shingles & hash-fns]
  (let [U         (map char->row (apply set/union doc-shingles))
        C         (characteristics doc-shingles)
        hash-sigs (map (apply juxt hash-fns) U)
        sig-rows  (map (fn [row sig]
                         (map #(if %
                                 sig
                                 (vec (take (count sig)
                                            (repeat Integer/MAX_VALUE))))
                              row))
                       (transpose C) hash-sigs)]
    (reduce merge-vectors sig-rows)))
;; (signatures [S1 S2 S3 S4] hash-1 hash-2)
;; (map (fn [row sig] ...) matrix sigs)
;; => ([[1 1] nil nil [1 1]] ...)
;; (reduce merge-sigs (vec (cycle c nil)) sig-partials)
(comment
  (signatures docs hash-1 hash-2))

(defn read)
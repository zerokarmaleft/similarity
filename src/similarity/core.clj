(ns similarity.core
  (:require [clojure.java.io :as io]
            [clojure.math.combinatorics :as comb]
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

(defn sim-vector
  "Returns the similarity of signature vectors as a relative size of their intersection"
  [s t]
  (letfn [(sim-vector-iter [s t n-intersect n-union]
            (let [[x xs] s
                  [y ys] t]
              (if (or (empty? s) (empty? t))
                (/ n-intersect n-union)
                (if (= x y)
                  (recur (rest s) (rest t) (inc n-intersect) (inc n-union))
                  (recur (rest s) (rest t) n-intersect (inc n-union))))))]
    (sim-vector-iter s t 0 0)))

(comment
  (sim-vector [1 0] [0 1])
  (sim-vector [1 0 0] [0 0 1])
  (sim-vector [1 0 0] [0 0 0])
  (sim-vector [1 1 1 1] [1 1 1 0])
  (sim-vector [1 1 1 1] [1 1 1 1]))

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
    (sim-jaccard D1 D2)))

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

(comment
  (characteristics [S1 S2 S3 S4]))

(defn count-cols [C]
  (count C))

(defn count-rows [C]
  (reduce max (map count C)))
(def C (characteristics docs))

(defn char->row [c] (- (int c) 97))
(defn hash-1 [x] (mod (+ x 1) 5))
(defn hash-2 [x] (mod (+ (* x 3) 1) 5))
(defn hash-3 [x] (mod (+ (* x 2) 4) 5))
(defn hash-4 [x] (mod (- (* x 3) 1) 5))

(defn transpose
  [C]
  (partition (count C) (apply interleave C)))

(defn merge-vectors
  [v1 v2]
  (map #(map min %1 %2) v1 v2))

(defn signatures
  [doc-shingles & hash-fns]
  (let [U         (apply set/union doc-shingles)
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

(comment ; Example 3.8
  (signatures docs hash-1 hash-2)
  (let [[d1 d2 d3 d4] (signatures docs hash-1 hash-2)]
    {[:d1 :d2] (sim-vector d1 d2)
     [:d1 :d3] (sim-vector d1 d3)
     [:d1 :d4] (sim-vector d1 d4)
     [:d2 :d3] (sim-vector d2 d3)
     [:d2 :d4] (sim-vector d2 d4)
     [:d3 :d4] (sim-vector d3 d4)}))

(comment ; Exercise 3.3.2
  (signatures docs hash-1 hash-2 hash-3 hash-4)
  (let [[d1 d2 d3 d4] (signatures docs hash-1 hash-2 hash-3 hash-4)]
    {[:d1 :d2] (sim-vector d1 d2)
     [:d1 :d3] (sim-vector d1 d3)
     [:d1 :d4] (sim-vector d1 d4)
     [:d2 :d3] (sim-vector d2 d3)
     [:d2 :d4] (sim-vector d2 d4)
     [:d3 :d4] (sim-vector d3 d4)}))

(comment ; Exercise 3.3.3
  (let [S1     #{2 5}
        S2     #{0 1}
        S3     #{3 4}
        S4     #{0 2 4}
        docs   [S1 S2 S3 S4]]
    (letfn [(hash-1 [x] (mod (+ (* 2 x) 1) 6))
            (hash-2 [x] (mod (+ (* 3 x) 2) 6))
            (hash-3 [x] (mod (+ (* 5 x) 2) 6))]
      (let [[d1 d2 d3 d4] (signatures docs hash-1 hash-2 hash-3)]
        {:signatures [d1 d2 d3 d4]
         [:d1 :d2]   [(sim-vector d1 d2) (sim-jaccard S1 S2)]
         [:d1 :d3]   [(sim-vector d1 d3) (sim-jaccard S1 S3)]
         [:d1 :d4]   [(sim-vector d1 d4) (sim-jaccard S1 S3)]
         [:d2 :d3]   [(sim-vector d2 d3) (sim-jaccard S2 S3)]
         [:d2 :d4]   [(sim-vector d2 d4) (sim-jaccard S2 S4)]
         [:d3 :d4]   [(sim-vector d3 d4) (sim-jaccard S3 S4)]}))))

(use 'clojure.pprint)

(comment ; Section 3.4
  (let [k-shingles (partial shingles 4)
        d1   (k-shingles "The quick brown fox jumps over the lazy dog.")
        d2   (k-shingles "The quick white wolf eats the lazy sheep.")
        d3   (k-shingles "The slow brown fox jumps into the quizzical dog.")
        d4   (k-shingles "The slow white wolf lays next to the lazy dog.")
        d5   (k-shingles "The quick brown fox jumps over the lazy cat.")
        docs [d1 d2 d3 d4 d5]
        U    (sort (apply set/union docs))
        C    (characteristics docs)
        hash-1 (fn [s] (mod (+ (* 2 (.hashCode s)) 1) (count U)))
        hash-2 (fn [s] (mod (+ (* 3 (.hashCode s)) 2) (count U)))
        hash-3 (fn [s] (mod (+ (* 5 (.hashCode s)) 2) (count U)))
        hash-4 (fn [s] (mod (+ (* 2 (.hashCode s)) 4) (count U)))
        hash-5 (fn [s] (mod (- (* 3 (.hashCode s)) 1) (count U)))
        hash-6 (fn [s] (mod (+ (* 5 (.hashCode s)) 4) (count U)))
        hash-fns [hash-1 hash-2 hash-3 hash-4 hash-5 hash-6]
        hash-sigs (map (apply juxt [hash-1 hash-2]) U)
        sig-rows  (map (fn [row sig]
                         (map #(if %
                                 sig
                                 (vec (take (count sig)
                                            (repeat Integer/MAX_VALUE))))
                              row))
                       (transpose C) hash-sigs)
        [s1 s2 s3 s4 s5] (signatures docs hash-1 hash-2 hash-3 hash-4 hash-5 hash-6)
        sim-docs (map #(apply sim-jaccard %)
                      (comb/combinations [d1 d2 d3 d4 d5] 2))
        sim-sigs (map #(apply sim-vector %)
                      (comb/combinations [s1 s2 s3 s4 s5] 2))]
    (pprint {:signatures [s1 s2 s3 s4 s5]
             :sim-docs   sim-docs
             :sim-sigs   sim-sigs
             :error      (map #(- %1 %2) sim-docs sim-sigs)})))
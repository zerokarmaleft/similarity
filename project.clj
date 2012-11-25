(defproject similarity "0.1.0-SNAPSHOT"
  :description "Finding similar items with locality-sensitive hashing"
  :url "http://github.com/zerokarmaleft/similarity"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/math.combinatorics "0.0.3"]
                 [cascalog "1.10.0"]
                 [cascalog-more-taps "0.3.0"]
                 [com.google.guava/guava "13.0.1"]]
  :profiles {:provided {:dependencies [[org.apache.hadoop/hadoop-core "0.20.2-dev"]]}}
  :main similarity.core
  :aot [similarity.core]
  :uberjar-name "similarity.jar")

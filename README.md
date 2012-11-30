# similarity

Finding similar documents with minhashing/locality-sensitive hashing.

## Usage

[Install Leiningen](https://github.com/technomancy/leiningen#installation).

Clone the repository and download dependencies.
```
	$ git clone https://github.com/zerokarmaleft/similarity.git
	$ cd similarity
	$ lein deps
```

The easiest way to get started is by exploring the sample in-memory datasets with the REPL.  Launch a Clojure REPL with Leiningen:
```
	$ lein repl
```
Then, at the REPL prompt, load the example datasets in the `playground` namespace and bootstrap the REPL environment so Hadoop's logging output is hooked into standard out:
```
	similarity.core=> (use 'similarity.playground)
	nil
	similarity.core=> (in-ns 'similarity.playground)
	#<Namespace similarity.playground>
	similarity.playground=> (bootstrap)
	nil
```
Finally, pretty print the sample dataset, which is simply a vector of 2-tuples. Each 2-tuple represents a document's id and content, respectively. The `similarity` query takes 5 parameters - the input dataset, the document id, the threshold for similarity (between 0.0 and 1.0), the size of the k-shingles, the number of hash functions used for minhashing, and the number of bands used for locality-sensitive hashing (which must divide the previous parameter for hashing into bands with equal number of rows).
```
	similarity.playground=> (use 'clojure.pprint)
	nil
	similarity.playground=> (pprint documents)
	[["docA"
	  "A pangram is a phrase that contains all of the letters of the English alphabet. The quick brown fox jumps over the lazy dog. This is a pangram."]
	 ["docB"
	  "A pangram is a phrase that contains all of the letters of the English alphabet. The quick white wolf eats the lazy sheep."]
	 ["docC"
	  "A pangram is a phrase that contains all of the letters of the English alphabet. The slow brown fox jumps into the quizzical dog."]
	 ["docD"
	  "A pangram is a phrase that contains all of the letters of the English alphabet. The slow white wolf lays next to the lazy dog."]
	 ["docE"
	  "A pangram is a phrase that contains all of the letters of the English alphabet. The quick brown fox jumps over the lazy cat."]]
	nil
	similarity.playground=> (?- (stdout) (similarity documents "docA" 0.6 4 1000))
```

To run with Hadoop locally, build an uberjar (which packages the job, and all dependencies, including Clojure into a single JAR).
```
	$ lein uberjar
	$ hadoop jar target/similarity.jar <input path> <output path> <document index> <similarity threshold> <size of k-shingles> <number of hash functions> <number of bands for LSH>
```

## License

Copyright © 2012 Edward Cho.

Distributed under the Eclipse Public License, the same as Clojure.

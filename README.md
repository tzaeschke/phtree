# PH-Tree

The PH-tree is a multi-dimensional indexing and storage structure.
By default it stores k-dimensional keys (points) consisting of k 64bit-integers. However, it can also be used to efficiently store floating point values and/or k-dimensional rectangles.
It supports kNN (k nearest neighbor) queries, range queries, window queries and fast update/move/reinsert of individual entries.

Documents

- The PH-tree was developed at ETH Zurich and first published in:
"The PH-Tree: A Space-Efficient Storage Structure and Multi-Dimensional Index" ([PDF](https://github.com/tzaeschke/phtree/blob/master/PH-Tree-v1.1-2014-06-28.pdf)), 
Tilmann Zäschke, Christoph Zimmerli and Moira C. Norrie, 
Proceedings of Intl. Conf. on Management of Data (SIGMOD), 2014
- The current version of the PH-tree is discussed in more detail in this [PDF](https://github.com/tzaeschke/phtree/blob/master/PhTreeRevisited.pdf) (2015).
- There is a Master Thesis: [Cluster-Computing and Parallelization for the Multi-Dimensional PH-Index](http://e-collection.library.ethz.ch/eserv/eth:47729/eth-47729-01.pdf).
- The hypercube navigation is discussed in detail in this [PDF](https://github.com/tzaeschke/phtree/blob/master/Z-Ordered_Hypercube_Navigation.pdf) (2017).


Maven:

```
<dependency>
    <groupId>ch.ethz.globis.phtree</groupId>
    <artifactId>phtree</artifactId>
    <version>2.0.1</version>
</dependency>
```

A C++ version of the PH-Tree (with slightly different design) is available [here](https://github.com/mcxme/phtree).

**_Other spatial indexes can be found in the [TinSpin spatial index collection](https://github.com/tzaeschke/tinspin-indexes)._**


# News

### 2018-05-30

Released version 2.0.1 of the PH-Tree. This release contains some minor fixes and documentation updates.

### 2018-05-29

Released version 2.0.0 of the PH-Tree (partial reimplementation). There are three new versions:

- v13 has a new much better kNN query then previous versions, but has otherwise only small improvements over 
  the previous v11. v13 is the best version for less than 8 dimensions.
- v16 and v16HD are reimplementations of the PH-Tree. The basic concept is still the same, except that the internal
  structure of nodes is now a B+Tree instead of the previous AHC/LHC nodes. Advantages:
  * Much simpler code.
  * Insertion/removal performance scales much better with dimensionality.
  * The v16HD version supports theoretically up to 2^31 dimensions.
  * Downside: memory requirements have increased, they are now on par with R*Trees or kD-trees.
  * Internal B+Tree structure (with configurable page sizes) makes it more suitable for disk based storage.
  * **API Contract Change**: The PH-Tree now stores keys (long[]/double[]) internally. Modifying them
  after storing them in the tree will make the tree invalid.
- The `PhTree` factory class will automatically choose one of v13, v16 and v16HD, depending on the number of dimensions. 


### 2017-09-17

Released version 0.3.4 of the PH-tree

- Bugfix: kNN distance returned '0' for PhTreeF.

### 2017-03-05

Released version 0.3.3 of the PH-tree

- Some bugfixes.
- Updated documentation.

### 2016-09-09

Released version 0.3.2 of the PH-tree

- Added support for kNN nearest neighbor queries for rectangle data.
- More API updates (javadoc and added missing methods).


### 2016-08-01

Released version 0.3.1 of the PH-tree

- Unified object pool configuration
- javadoc to compile with -Xlint:all

### 2016-08-23

Released version 0.3.0 of the PH-tree (internal version: v11). Features (partly available before, but not in the original version):

- Major code refactoring
- Restructuring of node data, subnodes and data are now in the same collection. This is faster and simplifies the code but requires slightly more memory
- AHC vs LHC policy has changed to prefer faster (but larger) AHC nodes at the cost of memory. Set  `Node.AHC_LHC_BIAS = 1.0` for lowest memory requirements.
- Dedicated reinsertion / update methods
- Nearest neighbor queries (reimplemented since v8)
- Support for rectangle data
- Reduced garbage collection load: query iterators are reusable, returned entries are reusable, objects are pooled internally
- Performance improvements and bug fixes
- Available as maven artifact  


# Main Properties

There are currently two main versions, the classic PH-Tree (called **PH1**, latest implementation v13) and the new PH-Tree (called **PH2**, latest implementation v16/v16HD).

### Advantages

- Memory efficient (PH1 only): Due to prefix sharing and other optimizations the tree may consume less memory than a flat array of integers/floats.
- Update efficiency: The performance of `insert()`, `update()` and `delete()` operations is almost independent of the size of the tree. For low dimensions performance may even increase(!) with growing tree size (> 1M entries).
- Small queries / spatial join: The PH-Tree (**PH1 only**) excels at 'small' window queries (small = small result size, such as 0 or 1). Experiments have shown that a brute force spatial join with the PH-Tree may be faster than dedicated solutions such as [TOUCH](https://infoscience.epfl.ch/record/186338/files/sigfp132-nobari_1.pdf) (simulating experiments as described in the TOUCH paper, PH1 with 'multiply' preprocessing).  
- Scalability with size: The tree scales very with size especially with larger datasets with 1 million entries or more.
- Scalability with dimension: Updates and 'contains()' scale almost horizontally with increasing dimensionality. Depending on the dataset, window queries may scale up to 20 dimensions or more. _k_NN (nearest neighbor) queries also scale well, much better than kD-trees, but can't quite compete with specialized solution such as CoverTree. 
- Skewed data: The tree works very well with skewed datasets, it actually prefers skewed datasets over evenly distributed datasets. However, see below (Data Preprocessing) for an exception.
- Stability: The tree never performs rebalancing, but imbalance is inherently limited so it is not a concern (maximum depth is 64, see paper). The advantages are that any modification operation will never modify more than one node in the tree. This limits the possible CPU cost and IO cost of update operations. It also makes is suitable for concurrency, see also the section on concurrency below.
- IO / persistence: The nodes of PH2 use internally B+Trees. These lend themselves to page base storage, at least for higher dimensions where nodes contain more entries.


### Disadvantages

- The PH1 will not work with more then 62 dimensions. The PH2 supports up to 2^31 dimensions but at the cost of increased memory requiremts.
- Performance/size: the tree generally performs less well with smaller datasets, is is best used with 1 million entries or more.
- Performance/dimensionality: depending on the dataset, performance of window queries may degrade when using data with more than 30 dimensions. 
- Data: The tree may degrade with extreme datasets, as described in the paper. However it will still perform better than traditional KD-trees. Furthermore, the degradation can be avoided by preprocessing the data, see below.
- Storage (PH1 only): The tree does not store references to the provided keys, instead it compresses the keys into in internal representation. As a result, when extracting keys (for example via queries), new objects (`long[]`) are created to carry the returned keys. This may cause load on the garbage collector if the keys are discarded afterwards. See the section about _iterators_ below on some strategies to avoid this problem. 



### Generally

- The tree performs best with large datasets with 1 million entries or more. Performance may actually increase with large datasets.
- The tree performs best on window queries or nearest neighbor queries that return few result (window queries: 1-1000) because of the comparatively high extraction cost of values. 


### Differences to original PH-Tree

PH1 introduced:
- Support for rectangle data
- Support for _k_ nearest neighbor queries
- Dedicated `update()` method that combines `put()` and `remove()`
- Automatic splitting of large nodes greatly improves update performance for data with more than 10 dimensions
- General performance improvements and reduced garbage collection load

PH2 introduced:
- Much simpler implementation based on B+Trees (instead of AHC/LHC nodes with bitstreaming).
- Support for >62 dimensions, theoretical limit now 2^31 dimensions
- New _k_NN nearest neighbor search following [Hjaltason and Samet: "Distance browsing in spatial databases."](https://pdfs.semanticscholar.org/0c67/15c8b7e8239cf7d7703e11a88e9cd5ab7714.pdf).
- Generally better insertion/update performance for dim>8


# Interfaces / Abstract Classes

This archive contains four variants and multiple versions of the PH-tree.

The four variants are:

- `PhTree`          For point data with integer coordinates. This is the native storage format.
- `PhTreeF`         For point data with floating point coordinates.
- `PhTreeSolid`     For intervals/rectangles/boxes (solids) with integer coordinates.
- `PhTreeSolidF`    For intervals/rectangles/boxes (solids) with floating point coordinates.

They can be created with `PhTreeXYZ.create(dimensions)`. This will create a PH1 tree (v13) for small dimensions, a PH2 (v16) for dimensions up to 60 and a PH2 (v16HD) if more dimensions are required.
The old non-value API is still available in the `tst` folder.
All queries return specialized iterators that give direct access to key, value or entry.
The `queryAll()` methods return lists of entries and are especially useful for small result sets. 

The packages `ch.ethz.globis.pht.v*` contain different versions of the PH-tree. They are the actual implementations of the four interfaces mentioned above.
A higher version number usually (not always) indicates better performance in terms of base speed,
scalability (size and dimensionality) as well as storage requirements.


# Tuning Memory Usage vs Performance

__**Configuration (PH1)**__

`Node.AHC_LHC_BIAS = 2.0`: This defines when the tree should switch from LHC to AHC representation for a given node. With the given default of 2.0, the tree uses AHC unless it requires more than 2.0 times the memory of LHC.
In practice this has only limited effect on memory, but a higher value tends to slightly increase performance of most operations. Commonly used values are 1.0, 1.5 and 2.0. 
 
`Node.NT_THRESHOLD = 150`: This defines when the tree should switch from LHC or AHC to NT (nested tree) representation for a given node. With the given default of 150, the tree uses NT for any node that has more than 150 entries (children + key/value entries). NT representation requires a lot more memory than LHC, but it also speeds up insert, remove and update operations on large nodes.
This parameters has profound impact on tree performance. For example, for dim=14, setting `NT_THRESHOLD=15000` increases insertion time by 400%, but reduce query time by 40%. This also affect kNN queries. Element-exists queries (point queries) are not affected. 
Using higher values reduces memory consumption but slows down any modification to the node. Commonly values are 150, 250 and 400. 

`NtNode.MAX_DIM = 6`: This defines the dimensionality of NT (nested tree) representation. For example, a d=20 dimensional node will be split in a d=6 root node, two levels of d=6 nodes below that any at the bottom a d=2 node. This adds up to 3*6+2=20. This was introduced to speed up modification (insert, remove, ...) of large nodes. Unfortunately, NT representation is very memory intensive.
This setting has not been well researched. Higher values should reduce memory consumption at the cost of modification speed. Commonly used values are 4, 6 and 8.

__**Configuration (PH2)**__

PH2 is currently not configurable. There are hardcoded page sizes for the internal B+Tree that can be found in the `Node` class, but these should have little impact on performance. 



__**Preprocessing and 32bit vs 64 bit**__

There is little point in using 32bit instead of 64bit integer values, because prefix sharing takes care of unused leading bits.
For floating point values, using a 32bit float instead of 64bit float should reduce memory usage
somewhat. However it is usually better to convert floating point values to integer values by multiplying them with a constant. For example multiply by 10E6 to preserve 6 digit floating point precision.
Also, chose the multiplier such that it is not higher than the precision requires.
For example, if you have a precision of 6 digits after the decimal point, then multiply all values
by 1,000,000 before casting the to (long) and adding them to the tree.


__**Garbage Collector**__

See also the section about _iterators_ (below) on how to avoid GC from performing queries.


# Perfomance Optimization

Suggestions for performance optimization can also be found in the PDF "The PH-Tree revisited", which is available in this repository.

### Updates

For updating the keys of entries (AKA moving objects index), consider using `update()`. This function is about twice as fast for small displacements and at least as fast as a `put()`/`remove()` combination.

### Choose a Type of Query

- `queryExtent()`:      Fastest option when traversing (almost) all of the tree
- `query()`:            Fastest option for for average result size > 50 (depending on data)
- `queryAll()`:         Fastest option for for average result size < 50 (depending on data)
- `nearestNeighbour()`: Nearest neighbor query
- `rangeQuery()`:       Returns everything with a spherical range

### Iterators (PH1)

All iterators return by default the value of a stored key/value pair. All iterators also provide
three specialized methods `nextKey()`, `nextValue()` and `nextEntry()` to return only the key, only the value (just as `next()`) or the combined entry object. Iterating over the entry object has the disadvantage that the entries need to be created and create load on the GC (garbage collector). However, the entries provide easy access to the key, especially for SOLID keys.

The `nextValue()` and `next()` methods do not cause any GC (garbage collector) load and simply return the value associated with the result key.
The `nextKey()` and `nextEntry()` always create new key objects or new key and additional `PhEntry` objects respectively. There are two ways to avoid this:
- During insert, one could store the key as part of the value, for example `insert(key, key)`. Then we can use the `next()` method to access the key without creating new objects. The disadvantage is that we are effectively storing the key twice, once as 'key' and once as 'value'. Since the PH-tree is quite memory efficient, this may still consume less memory than other trees. 
- During extraction, we can use the `PhQuery.nextEntryReuse()` method that is available in every iterator. It reuse `PhEntry` objects and key objects by resetting their content. Several calls to `nextEntryReuse()` may return the same object, but always with the appropriate content. The returned object is only valid until the next call to `nextEntryReuse()`.
The disadvantage is that the key and `PhEntry` objects need to be copied if they are needed locally beyond the next call to `nextEntryReuse()`.

Another way to reduce GC is to reuse the iterators when performing multiple queries. This can be done by calling `PhQuery.reset(..)`, which will abort the current query and reset the iterator to the first element that fits the min/max values provided in the `reset(..)` call. This can be useful because an iterator consists of more than hundred Java objects. In some scenarios this increased overall performance of about 20%.  

### Iterators (PH2)

In PH2, the iterators support the same interfaces as PH1, however there is less reuse happening. The reason is that PH2 does not compress keys internally and stores full entry objects. These can be directly returned. **However, care should be taken that returned objects are not modified, because that may invalidate the tree.**


### Wrappers

Another optimization to avoid GC may be to avoid or reimplement the wrappers (`PhTreeF`, `PhTreeSolid` and `PhTreeSolidF`). With most calls they create internally temporary objects for coordinates that are passed on to the actual tree (for example it creates a `long[]` for every `put` or `contains`). A custom wrapper could reuse these temporary objects so that they cannot cause garbage collection.


### Data Preprocessing

__**Default / IEEE**__

The default configuration of the PH-tree works quite well with most datasets. For floating point values it ensures that precision is fully maintained when points are converted to integer and back. The conversion is based on the IEEE bit representation, which is converted to an integer (see `BitTools.toSortableLong()`).   

__**Multiply**__

To optimise performance, it is usually worth trying to preprocess the data by multiplying it with a large integer and then cast it to `long`. The multiplier should be chosen such that the required precision is maintained. For example, if 6 fractional digits are required, the multiplier should be at least 10e6 or 10e7. There are some helper classes that provide predefined preprocessors that can be plugged into the trees. For example, a 3D rectangle-tree with an integer multiplier of 10e9 can be created with:  

`PhTreeSolidF.create(3, new PreProcessorRangeF.Multiply(3, 10e9));`

It is worth trying several multipliers, because performance may change considerably. For example,
for one of our tests we multiplied with 10e9, which performed 10-20% better than 10e8 or 10e10.
Typically, this oscillates with multiples of 1000, so 10e12 performs similar to 10e9. 

__**Shift / Add**__

If data should be stored as floats in IEEE representation (`BitTools.toSortableLong()`), consider adding a constant such that the whole value domain falls into a single exponent. I.e.
shift the values such that all values have the same exponent. It can also help to shift values
such that all values have a positive sign.

__**Heterogeneous**__

Heterogeneous data (different data types and value ranges in each dimension) can be problematic for the PH-Tree when performing queries (insert, update, delete, contains should not be affected).

For heterogeneous data (combination of floats, integers, boolean, ...) consider shifting the
values such that the min/max values in each dimension have a similar distance in the integer 
representation. For example a 3D tree: `[0...10][10..30][0..1000]` multiply the first dimension by
100 and the second by 50, so that all dimensions have a range of about 1000.

The above is true if all dimension are queried with similar selectivity. If range queries in the
above example would mainly constrain the 2nd and 3rd dimension, then the first dimension should
NOT be multiplied. In other words, the more selective queries are on a given dimension, the more
wide should the dimension spread over the tree, i.e. the dimension should be given a higher 
multiplier.

__**API Support**__

Data preprocessing can be automated using the `PreProcessor*` classes (partly known as `IntegerPP` or `ExponentPP` in the PDF documentation).

# Concurrency Support

The current has very limited support for concurrency.

### Read Only Access ###

Read only access (all types of queries) can safely be done by any number of threads. No synchronization is required.


### Read and Write Access ###

Any write access must be synchronized with any other concurrent write or read access (`iterator.next()` counts as read access). For example, a wrapper class could use a Java `ReadWriteLock` to ensure that write access is always exclusive:  

```
	private final ReadWriteLock lock = new ReentrantReadWriteLock(true);
	
	public void put(...) {
		Lock wlock = lock.writeLock();
		try {
			tree.put(key, value);
		} finally {
			wlock.unlock();
		}
	}
	
	public <T> ArrayList<T> query(...) {
		ArrayList<T> result = new ArrayList<>();
		Lock rlock = lock.readLock();
		try {
		    //This could be optimized by reusing the query object
		    //with PhQuery.reset()
			PhQuery<T> it = tree.query(...);
			while (it.hasNext()) {
				result.add(it.next());
        	}
		} finally {
			rlock.unlock();
		}
		return result;
	}	
``` 
 
It should be possible to allow even more fine grained access by also creating wrappers for the iterators, so that the read lock is only held during creation of the query and during each call to `next()`. Expected behavior: The query iterators may miss newly inserted entries or may return entries that have already been deleted. However, while this should work, it was never part of the current design and has not really been tested. If you find that it does not work (throws exception, missing entries that have not been modified, returns invalid data), let me know and I _may_ fix it if it doesn't impact general tree performance.
 
### Research ###
 
Generally, the PH-Tree should lends itself to concurrent implementations, because it is guaranteed that no call to `put` or `remove` will ever affect more than two nodes. In fact, only one node will ever be modified with possibly a second one added or removed.

There is a Master Thesis that explores concurrent implementations (copy on write, node level locking, ...), see Section 6.2 in
[Cluster-Computing and Parallelization for the Multi-Dimensional PH-Index](http://e-collection.library.ethz.ch/eserv/eth:47729/eth-47729-01.pdf).
The source code is not maintained anymore and lacks a number of features of the latest PH-Tree (kNN queries, update, better performance, ...). However, I can make it available on request. 
  
 

# License

The code is licensed under the Apache License 2.0.

The PH-tree (namespace `ch.ethz`) is copyright 2011-2016 by 
ETH Zurich,
Institute for Information Systems,
Universitätsstrasse 6,
8092 Zurich,
Switzerland.

The critbit tree (namespace `org.zoodb`) is copyright 2009-2018 by
Tilmann Zäschke,
zoodb@gmx.de.
The critbit tree (and other spatial indexes) are also separately available [here](https://github.com/tzaeschke/zoodb-indexes)

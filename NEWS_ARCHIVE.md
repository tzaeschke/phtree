# News Archive

### 2023-06-26
- Added new multimap: `PhTreeMultiMapF2`.

### 2020-04-30
Release 2.5.0
- Added convenience API for multi-map: `PhTreeMultiMapF`. This API emulates a PH-Tree that (unlike normal PH-Trees)
  support multiple entries for any coordinate. This is enabled by storing a unique identifier
  in an additional dimension.
  For performance reasons it is recommended to use small absolute values for IDs, such as 16bit or 32bit integers.

### 2019-11-10
Release 2.4.0
- Added missing public API for filtered queries: `PhTree.query(min, max, filter)`

### 2019-03-19
Release 2.3.0
- Added missing compute functions for `PhTreeF`, `PhTreeSolid` and `PhTreeSolidF`
- Fixed bug in `compute()`/`computeIfPresent()` in V13

### 2019-03-15
Release 2.2.0
- Added Java 8 Map API to V13 and V16 (`putIfAbsent()`, `compute()`, ...). Only `compute()` and `computeIfPresent()` are currently optimized.
- Several minor speed improvements to V13 and V16, including reduced garbage creation.

### 2019-03-08
Released version 2.1.0 of the PH-Tree. 
- Avoid 'synchronized' object pooling. Object pooling in V13, v16 and v16HD have been modified to be non-`synchronized`, instead each instance of the PhTree has its own pool. A a result, running several PhTree instances in parallel will slightly increase memory usage (due to several pools allocated), butt will completely avoid contention caused by `synchronized` pools. A `synchronized` version of V13 is still available as `v13SynchedPool`.

### 2018-12-04
Released version 2.0.2 of the PH-Tree. This release contains a minor fix and documentation updates.

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

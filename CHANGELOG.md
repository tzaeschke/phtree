# Changelog

## Unreleased

## 2.8.0 - 2023-07-29
 
- Proper kNN implementation with MinMaxHeaps [#33](https://github.com/tzaeschke/phtree/pull/33)

## 2.7.0 - 2023-07-29

- Nothing

## 2.6.3 - 2023-07-10

- Fixed scope of JUnit dependency. [#30](https://github.com/tzaeschke/phtree/pull/30)

## 2.6.2 - 2023-07-06

- Fix new API for `PhQUeryF`/`PhEntentF`. [#29](https://github.com/tzaeschke/phtree/pull/29)

## 2.6.1 - 2023-07-06

- Fix new API for `PhEntryF`/`PhEntryDistF`. [#28](https://github.com/tzaeschke/phtree/pull/28)

## 2.6.0 - 2023-07-05

- CHANGELOG.md [#26](https://github.com/tzaeschke/phtree/pull/26)
- Added new multimap: PhTreeMultiMapF2. [#23](https://github.com/tzaeschke/phtree/pull/23)
- Added CI with GitHub actions. [#25](https://github.com/tzaeschke/phtree/pull/25)
- Updated maven dependencies. [#24](https://github.com/tzaeschke/phtree/pull/24)

- Tiny optimization: Change (value >=0.0) to (value >=0)
- Added tests for IEEE conversion

## 2.5.0 - 2020-04-30

- (TZ) Added convenience API for multi-map (PH-Tree with duplicates): PhTreeMultiMapF.

## 2.4.0 - 2019-11-10

- (TZ for Improbable) Added missing public API for filtered queries: PhTree.query(min, max, filter)

## 2.3.0 - 2019-03-18

- (TZ for Improbable) Fixed bug in compute()/computeIfPresent() in V13
- (TZ for Improbable) Added missing compute functions fot PhTreeF, PhTreeSolid and PhTreeSolidF
- (TZ for Improbable) Minor cleanup

## 2.2.0 - 2019-03-15

- (TZ for Improbable) Added Java 8 Map API to V13 and V16 (putIfAbsent(), compute(), ...)
  Only compute() and computeIfPresent() are currently optimized. The other methods use a naive implementation.
- (TZ for Improbable) Several minor speed improvements to V13 and V16, including reduced garbage creation.

## 2.1.0 - 2019-02-26

- (TZ for Improbable) Some cleanup and javadoc updates
- (TZ for Improbable) Improvements for running multiple PH-Trees concurrently:
    - removed AtomicInt entry counter
    - Changed Object/array pools to be unsynchronized and local (to the tree) instead of global and synchronized.

## 2.0.2 - 2018-12-04

- (TZ) Fixed NullPointerException in getStats()

## 2.0.1 - 2018-05-30

This release contains some minor fixes and documentation updates.

## 2.0.0 - 2018-05-29

There are three new versions of the PH-tree:

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

## 0.3.4 - 2017-09-17

- (TZ) Fixed bug PhTreeF kNN: distance was not returned.

## 0.3.3 - 2017-03-05

- (TZ) Fixed bug in CritBit (used by old PH-Tree), see
  https://github.com/tzaeschke/zoodb-indexes/issues/7
- (TZ) Update: PhTreeRevisited.pdf v1.2
- (TZ) Updated API classes to improve extensibility
- (TZ) Fixed bug in kNN for rectangles with 0 distance

## 0.3.2 - 2016-09-09

- (TZ) Added kNN queries support for rectangle data

## 0.3.1 - 2016-08-25

- Added support for kNN nearest neighbor queries for rectangle data.
- More API updates (javadoc and added missing methods).

## 0.3.0 - 2016-08-23

- (TZ) Added v11, lots of API changes

## ? - 2015-10-28

- (TZ) Added kNN-queries
- (TZ) Added (spherical) range queries
- (TZ) Numerous fixes, improvements, clean-up
- (TZ) Some API changes getDIM() -> getDim(), ...
- (TZ) Added PhTreeRevisited.pdf

## ? - 2015-10-11

- (TZ) Fixed possible NPE (in Critbit64COW) when using trees with k>6
- (TZ) Fixed memory waste in NodeEntries

## ? - 2015-08-31

- (TZ) Proper NoGC iterator for avoiding any object creation (+bug fix)
- (TZ) Changed iterator recursion to loops
- (TZ) Removed some old iterators and NV usage

## ? - 2015-08-03

- (TZ) PhQuery interface added. This allows resetting & reusing of query iterators.

## ? - 2015-07-30

- (TZ) Smaller NodeEntries + Avoid NodeEntry creation in some situations
- (TZ) Added experimental iterator that does not create new long[]...

## ? - 2015-07-29

- Added clear() method
- Fixed bug that prevented internal iterators from being reused (-> performance)
- Fixed another bug that caused queryAll to skip some matching keys.
  See TestIndexQueries.testBug64Neg_2()
- Fixed bug that caused queryAll to skip some matching keys.

## ? - 2015-06-01

- (TZ) Fixed bug that prevented internal iterators from being reused

## ? - 2015-05-25

- (TZ) Fixed another bug that caused queryAll to skip some matching keys.
  See TestIndexQueries.testBug64Neg_2()

## ? - 2015-05-24

- (TZ) Fixed bug that caused queryAll to skip some matching keys.

## ? - 2015-05-03

- API change! Refactored the API for simplification and removal of old non-value API.

## ? - 2015-03-03

- Significantly reduced object creation during insert/update/delete/query. This should reduce
  GC problems.
- New queryAll() function that returns a list of results instead of an iterator. This should
  be faster especially for small expected result sets.
 
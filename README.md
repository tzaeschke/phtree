# PH-tree

The PH-tree is a multi-dimensional indexing and storage structure.
By default it stores k-dimensional keys (points) consisting of k 64bit-integers. However, it can also be used
to efficiently store floating point values and/or ranged objects such as k-dimensional boxes.

The PH-tree was developed at ETH Zurich and first published in:
"The PH-Tree: A Space-Efficient Storage Structure and Multi-Dimensional Index", 
Tilmann Zaeschke, Christoph Zimmerli and Moira C. Norrie, 
Proceedings of Intl. Conf. on Management of Data (SIGMOD), 2014

Contact:
{zaeschke,zimmerli,norrie)@inf.ethz.ch


# Main properties

### Advantages

- Memory efficient: Due to prefix sharing and other optimisations the tree may consume less memory than a flat array of integers/floats
- Update efficieny: The performance of `insert()`, `update()` and `delete()` operations is almost independent of the size of the tree. For low dimensions performance may even improve with larger trees (> 1M entries).
- Scalability with size: The tree scales very with size especially with larger datasets with 1 million entries or more
- Scalability with dimension: Updates and 'contains()' queries scale very well to the current maximum of 62 dimensions. Range queries and other operations are best used with upto 10-15 dimensions only.
- Skewed data: The tree works very well with skewed datasets, it actually prefers skewed datasets over evenly distributed datasets. However, see below (Data Preprocessing) for an exception.
- Stability: The tree never performs rebalancing, but imbalance is inherently limited so it is not a concern (see paper). The advanatages are that any modification operation will never modify more than one node in the tree. This limits the possible CPU cost and IO cost of update operations. It also makes is suitable for concurrency.

### Disadvantages

- The current implementation will not work with mor then 62 dimensions.
- Performance/size: the tree generally performs less well with smaller datasets, is is best used with 1 million entries or more
- Performance/dimensionality: performance of range queries degrades when using data with more than 10-15 dimensions. Updates and `contains()` work fine for higher dimensions
- Data: The tree may degrade with extreme datasets, as described in the paper. However it will still perform better that traditional KD-trees. Furthermore, the degradation can be avoided by preprocessing the data, see below.
- Storage: The tree does not store references to the provided keys. That means, when extracting keys (for exampl via queries), new objects are created to return the keys. There are two solutions to that: 
  - First, it is possible to store the keys as 'values' in the tree via `put(key, key)` for example. Then, when iterating over the result, one should use this key stored in the value rather than the primary key. This allows the JVM to more easily collect the primary keys. There is also an experimental iterator (not public yet) that reuses primary key and thus avoid creation of any temporary objects.
  - Second, if the extracted keys are only used temporarily or if they get copied, then this can again simplify garbage collection, especially in combination with the experimental iterator mentioned above.


### Generally

- The tree performs best with large datasets.
- The tree performs best on range queries that return few result (1-1000) because of the comparatively high extraction cost of values. 


# Interfaces / Abstract Classes

This archive contains four variants and multiple versions of the PH-tree.

The four variants are:

- ```PhTree```          For point data with integer coordinates. This is the native storage format.
- ```PhTreeF```         For point data with floating point coordinates.
- ```PhTreeSolid```     For intervals/rectangles/boxes (solids) with integer coordinates.
- ```PhTreeSolidF```    For intervals/rectangles/boxes (solids) with floating point coordinates.

They can be created with ```PhTreeXYZ.create(dimensions)```. The default key-width is 64bit per dimension.
The old non-value API is still available in the ```tst``` folder.
All queries return specialised iterators that give direct access to key, value or entry.
The ```queryAll()``` methods return lists of entries and are especially useful for small result sets. 

The packages ```ch.ethz.globis.pht.v*``` contain different versions of the PH-tree. They are the actual
implementations of the four interfaces mentioned above.
A higher version number usually (not always) indicates better performance in terms of base speed,
scalability (size and dimensionality) as well as storage requirements.


# Tuning Memory Usage

There is little point in using 32bit instead of 64bit values, because prefix sharing takes care of
unused leading bits.
For floating point values, using a 32bit float instead of 64bit float should reduce memory usage
somewhat. However it is usually better to convert floating point values to integer values by a
constant. For example multiply by 10E6 to preserve 6 digit floating point precision.
Also, chose the multiplier such that it is not higher than the precision requires.
For example, if you have a precision of 6 digits after the decimal point, then multiply all values
by 1,000,000 before casting the to (long) and adding them to the tree.


# Perfomance Optimisation

### Updates

For updating the keys of entries (aka moving objects index), consider using ```update()```. This function
is about twice as fast for small displacements and at least as fast as a ```put()```/```remove()``` combo.

### Choose a type of query

- ```queryExtent()```: Fastest option when traversing (almost) all of the tree
- ```query()```:       Fastest option for for average result size > 50 (depending on data)
- ```queryAll()```:    Fastest option for for average result size < 50 (depending on data)


### Iterators

All iterators return by default the value of a stored key/value pair. All iterators also provide
three specialised methods ```nextKey()```, ```nextValue()``` and ```nextEntry()``` to return only the key, only the 
value (just as ```next()```) or the combined entry object. Iterating over the entry object has the 
disadvantage that the entries need to be created and create load on the GC. However, the entries
provide easy access to the key, especially for SOLID keys.


### Data preprocessing

To improve speed, similar measures can be applied as suggested for MEMORY. For example it makes 
sense to transform values into integers by multiplication with a constant.

If data is stored as floats in IEEE representation (```BitTools.toSortableLong()```), consider adding
or multiplying a constant such that the whole value domain falls into a single exponent. I.e.
shift the values such that all values have the same exponent. It can also help to shift values
such that all values have a positive sign.

For heterogenous data (combination of floats, integers, boolean, ...) consider shifting the
values such that the min/max values in each dimension have a similar distance in the integer 
representation. For example a 3D tree: [0...10][10..30][0..1000] multiply the first dimension by
100 and the second by 50, so that all dimensions have a range of about 1000.

The above is true if all dimension are queried with similar selectivity. If range queries in the
above example would mainly constrain the 2nd and 3rd dimension, then the first dimension should
NOT be multiplied. In other words, the more selective queries are on a given dimension, the more
wide should the dimension spread over the tree, i.e. the dimension should be given a higher 
multiplier.

  
# License

The PH-tree (namespace ```ch.ethz```) is copyright 2011-2015 by 
ETH Zurich,
Institute for Information Systems,
Universitätsstrasse 6,
8092 Zurich,
Switzerland.

The critbit tree (namespace ```org.zoodb```) is copyright 2009-2015 by
Tilmann Zäschke,
zoodb@gmx.de.
The critbit tree is also separately available here: [https://github.com/tzaeschke/critbit]

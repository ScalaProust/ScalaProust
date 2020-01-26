package scala.concurrent.stm.traditional

import scala.concurrent.stm.{TArray, InTxn, Ref}
import scala.collection.AbstractIterator

trait HashEntry[A, Entry <: HashEntry[A, Entry]] {
	private class MakeDefault{
		var e:Entry = _
				def apply():Entry = e
	}
	val key: A
	val next:Ref[Entry] = Ref((new MakeDefault)())
}

/** This class can be used to construct data structures that are based
 *  on hashtables. Class `STMHashTable[A]` implements a hashtable
 *  that maps keys of type `A` to values of the fully abstract
 *  member type `Entry`. Classes that make use of `STMHashTable`
 *  have to provide an implementation for `Entry`.
 *
 *  There are mainly two parameters that affect the performance of a hashtable:
 *  the <i>initial size</i> and the <i>load factor</i>. The <i>size</i>
 *  refers to the number of <i>buckets</i> in the hashtable, and the <i>load
 *  factor</i> is a measure of how full the hashtable is allowed to get before
 *  its size is automatically doubled. Both parameters may be changed by
 *  overriding the corresponding values in class `STMHashTable`.
 *
 *  @author  Matthias Zenger
 *  @author  Martin Odersky
 *  @version 2.0, 31/12/2006
 *  @since   1
 *
 *  @tparam A     type of the elements contained in this hash table.
 */
trait HashTable[A] extends HashTable.HashUtils[A] {
	// Replacing Entry type parameter by abstract type member here allows to not expose to public
	// implementation-specific entry classes such as `DefaultEntry` or `LinkedEntry`.
	// However, I'm afraid it's too late now for such breaking change.
	import HashTable._

	protected[this] type Entry >: Null <: HashEntry[A, Entry]

	// This is possibly less space efficient than TArray, but it's also way less annoying to deal with all of the manifest garbage
	private[this] def allocTable(capacity:Int):Array[Ref[Entry]] = Array.fill(capacity)(Ref(null))

	protected val _loadFactor: Ref[Int] = Ref(defaultLoadFactor)

	/** The actual hash table.
	 *  If we have to resize the table we will need to assign a new value here, so we need, Ref[Array[Ref[T]]] and not just Array[Ref[T]]
	 */
	protected val table: Ref[Array[Ref[Entry]]] = Ref(allocTable(initialCapacity))

	/** The number of mappings contained in this hash table.
	 */
	protected val tableSize: Ref[Int] = Ref(0)

	/** The next size value at which to resize (capacity * load factor).
	 */
	protected val threshold: Ref[Int] = Ref(initialThreshold(_loadFactor.single()))

	/** The array keeping track of the number of elements in 32 element blocks.
	 */
	protected val sizemap: Ref[Array[Ref[Int]]] = Ref(null)

	protected val seedvalue: Ref[Int] = Ref(tableSizeSeed)

	protected def tableSizeSeed = Integer.bitCount(table.single().length - 1)

	/** The initial size of the hash table.
	 */
	protected def initialSize: Int = 16

	/** The initial threshold.
	 */
	private def initialThreshold(_loadFactor: Int): Int = newThreshold(_loadFactor, initialCapacity)

	private def initialCapacity = capacity(initialSize)

	private def lastPopulatedIndex (implicit txn: InTxn) = {
		var idx = table().length - 1
		while (table()(txn)(idx)() == null && idx > 0){ idx -= 1 }
		idx
	}

	/** Find entry with given key in table, null if not found.
	 */
	protected final def findEntry(key: A)(implicit txn: InTxn): Entry = findEntry0(key, index(elemHashCode(key)))
	private[this] def findEntry0(key: A, h: Int)(implicit txn: InTxn): Entry = {
		var e:Entry = table()(txn)(h)() // Apparently the compiler can't figure out to stick the txn there due to all of syntax sugar
		while (e != null && !elemEquals(e.key, key)){ e = e.next() }
		e
	}

	/** Add entry to table
	 *  pre: no entry with same key exists
	 */
	protected final def addEntry(e: Entry)(implicit txn: InTxn):Unit = addEntry0(e, index(elemHashCode(e.key)))
	private[this] def addEntry0(e: Entry, h: Int)(implicit txn: InTxn):Unit = {
		e.next() = table()(txn)(h)()
		table()(txn)(h)() = e
		tableSize += 1
		nnSizeMapAdd(h)
		if (tableSize() > threshold()) { resize(2 * table().length) }
	}

	/** Find entry with given key in table, or add new one if not found.
	 *  May be somewhat faster then `findEntry`/`addEntry` pair as it
	 *  computes entry's hash index only once.
	 *  Returns entry found in table or null.
	 *  New entries are created by calling `createNewEntry` method.
	 */
	protected def findOrAddEntry[B](key: A, value: B)(implicit txn: InTxn): Entry = {
		val h = index(elemHashCode(key))
		val e:Entry = findEntry0(key, h)
		if (e ne null) { e } else { addEntry0(createNewEntry(key, value), h); null }
	}

	/** Creates new entry to be immediately inserted into the hashtable.
	 *  This method is guaranteed to be called only once and in case that the entry
	 *  will be added. In other words, an implementation may be side-effecting.
	 */
	protected def createNewEntry[B](key: A, value: B): Entry

	/** Remove entry from table if present.
	 */
	protected final def removeEntry(key: A)(implicit txn: InTxn) : Entry = {
		val h:Int = index(elemHashCode(key))
		var e:Entry = table()(txn)(h)()
		if (e != null) {
			if (elemEquals(e.key, key)) {
				table()(txn)(h)() = e.next()
				tableSize -= 1
				nnSizeMapRemove(h)
				e.next() = null
				return e
			} else {
				var e1:Entry = e.next()
				while (e1 != null && !elemEquals(e1.key, key)) {
					e = e1
					e1 = e1.next()
				}
				if (e1 != null) {
					e.next() = e1.next()
					tableSize -= 1
					nnSizeMapRemove(h)
					e1.next() = null
					return e1
				}
			}
		}
		null
	}

	/** An iterator returning all entries.
	 */
	protected def entriesIterator (implicit txn: InTxn): Iterator[Entry] = new AbstractIterator[Entry] {
		val iterTable = table()
		var idx       = lastPopulatedIndex
		var es:Entry        = iterTable(idx)()

		def hasNext = es != null
		def next() = {
			val res = es
			es = es.next()
			while (es == null && idx > 0) {
				idx -= 1
				es = iterTable(idx)()
			}
			res
		}
	}

	/** Avoid iterator for a 2x faster traversal. */
	protected def foreachEntry[U](f: Entry => U)(implicit txn: InTxn):Unit = {
		val iterTable = table()
		var idx       = lastPopulatedIndex
		var es:Entry        = iterTable(idx)()

		while (es != null) {
			val next = es.next() // Cache next in case f removes es.
			f(es)
			es = next

			while (es == null && idx > 0) {
				idx -= 1
				es = iterTable(idx)()
			}
		}
	}

	/** Remove all entries from table
	 */
	protected def clearTable(implicit txn: InTxn) = {
		var i = table().length - 1
		while (i >= 0) { table()(i) = null; i-= 1 }
		tableSize() = 0
		nnSizeMapReset(0)
	}

	private def resize(newSize: Int)(implicit txn: InTxn) = {
		val oldTable = table()
		table() = allocTable(newSize)
		nnSizeMapReset(table().length)
		var i = oldTable.length - 1
		while (i >= 0) {
			var e:Entry = oldTable(i)()
			while (e != null) {
				val h = index(elemHashCode(e.key))
				val e1 = e.next()
				e.next() = table()(txn)(h)()
				table()(txn)(h)() = e
				e = e1
				nnSizeMapAdd(h)
			}
			i = i - 1
		}
		threshold() = newThreshold(_loadFactor(), newSize)
	}

	/* Size map handling code */

	/*
	 * The following three sizeMap* functions (Add, Remove, Reset)
	 * are used to update the size map of the hash table.
	 *
	 * The size map logically divides the hash table into `sizeMapBucketSize` element buckets
	 * by keeping an integer entry for each such bucket. Each integer entry simply denotes
	 * the number of elements in the corresponding bucket.
	 * Best understood through an example, see:
	 * table   = [/, 1, /, 6, 90, /, -3, 5]    (8 entries)
	 * sizemap = [     2     |     3      ]    (2 entries)
	 * where sizeMapBucketSize == 4.
	 *
	 * By default the size map is not initialized, so these methods don't do anything, thus,
	 * their impact on hash table performance is negligible. However, if the hash table
	 * is converted into a parallel hash table, the size map is initialized, as it will be needed
	 * there.
	 */
	protected final def nnSizeMapAdd(h: Int)(implicit txn: InTxn):Unit = { 
		if(isSizeMapDefined){
			val entry = sizemap()(txn)(h >> sizeMapBucketBitSize)
			entry += 1
		}
	}

	protected final def nnSizeMapRemove(h: Int)(implicit txn: InTxn):Unit = { 
		if(isSizeMapDefined){
			val entry = sizemap()(txn)(h >> sizeMapBucketBitSize)
			entry -= 1
		}
	}

	protected final def nnSizeMapReset(tableLength: Int)(implicit txn: InTxn):Unit = {
		if(isSizeMapDefined){
			val nsize = calcSizeMapSize(tableLength)
			if (sizemap().length != nsize) sizemap() = Array.fill(nsize)(Ref(0))
			else sizemap().foreach{ _() = 0 }
		}
	}

	private[traditional] final def totalSizeMapBuckets(implicit txn: InTxn):Int = { 
		if (sizeMapBucketSize < table().length) { 
			1 
		} else {
			table().length / sizeMapBucketSize 
		}
	}

	protected final def calcSizeMapSize(tableLength: Int):Int = (tableLength >> sizeMapBucketBitSize) + 1

	// discards the previous sizemap and only allocates a new one
	protected def sizeMapInit(tableLength: Int)(implicit txn: InTxn):Unit = {
		sizemap() = Array.fill(calcSizeMapSize(tableLength))(Ref(0))
	}

	// discards the previous sizemap and populates the new one
	protected final def sizeMapInitAndRebuild(implicit txn: InTxn):Unit = {
		sizeMapInit(table().length)

		// go through the buckets, count elements
		var tableidx = 0
		var bucketidx = 0
		var tableuntil = 0
		if (table().length < sizeMapBucketSize) tableuntil = table().length else tableuntil = sizeMapBucketSize
		val totalbuckets = totalSizeMapBuckets
		while (bucketidx < totalbuckets) {
			var currbucketsize = 0
			while (tableidx < tableuntil) {
				var e:Entry = table()(txn)(tableidx)()
				while (e ne null) {
					currbucketsize += 1
					e = e.next()
				}
				tableidx += 1
			}
			sizemap()(txn)(bucketidx)() = currbucketsize
			tableuntil += sizeMapBucketSize
			bucketidx += 1
		}
	}

	private[traditional] def printSizeMap(implicit txn: InTxn) {
		println(arrayString(sizemap(),"Int"))
	}

	protected final def sizeMapDisable(implicit txn: InTxn) = { sizemap() = null }
	protected final def isSizeMapDefined(implicit txn: InTxn) = { sizemap() ne null }

	// override to automatically initialize the size map
	protected def alwaysInitSizeMap = false
	/* End of size map handling code */

	protected def elemEquals(key1: A, key2: A): Boolean = (key1 == key2)

	// Note:
	// we take the most significant bits of the hashcode, not the lower ones
	// this is of crucial importance when populating the table in parallel
	protected final def index(hcode: Int)(implicit txn: InTxn) = {
		val ones = table().length - 1
		val improved = improve(hcode, seedvalue())
		val shifted = (improved >> (32 - java.lang.Integer.bitCount(ones))) & ones
		shifted
	}

	protected def initWithContents(c: HashTable.Contents[A, Entry])(implicit txn: InTxn) = {
		if (c != null) {
			_loadFactor() = c.loadFactor
			table() = c.table
			tableSize() = c.tableSize
			threshold() = c.threshold
			seedvalue() = c.seedvalue
			sizemap() = c.sizemap
		}
		if (alwaysInitSizeMap && sizemap == null) sizeMapInitAndRebuild
	}

	private[traditional] def hashTableContents(implicit txn: InTxn) = { 
		new HashTable.Contents[A, Entry](_loadFactor(), table(), tableSize(), threshold(), seedvalue(), sizemap())
	}
}

private[traditional] object HashTable {
	/** The load factor for the hash table (in 0.001 step).
	 */
	private[traditional] final def defaultLoadFactor: Int = 750 // corresponds to 75%
	private[traditional] final def loadFactorDenum = 1000
	private[traditional] final def newThreshold(_loadFactor: Int, size: Int) = ((size.toLong * _loadFactor) / loadFactorDenum).toInt
	private[traditional] final def sizeForThreshold(_loadFactor: Int, thr: Int) = ((thr.toLong * loadFactorDenum) / _loadFactor).toInt
	private[traditional] final def capacity(expectedSize: Int) = if (expectedSize == 0) 1 else powerOfTwo(expectedSize)

	trait HashUtils[KeyType] {
		protected final def sizeMapBucketBitSize = 5
		// so that:
		protected final def sizeMapBucketSize = 1 << sizeMapBucketBitSize

		protected def elemHashCode(key: KeyType) = key.##

		protected final def improve(hcode: Int, seed: Int) = {
			/* Murmur hash
			 *  m = 0x5bd1e995
			 *  r = 24
			 *  note: h = seed = 0 in mmix
			 *  mmix(h,k) = k *= m; k ^= k >> r; k *= m; h *= m; h ^= k; */
			// var k = hcode * 0x5bd1e995
			// k ^= k >> 24
			// k *= 0x5bd1e995
			// k

			/* Another fast multiplicative hash
			 * by Phil Bagwell
			 *
			 * Comment:
			 * Multiplication doesn't affect all the bits in the same way, so we want to
			 * multiply twice, "once from each side".
			 * It would be ideal to reverse all the bits after the first multiplication,
			 * however, this is more costly. We therefore restrict ourselves only to
			 * reversing the bytes before final multiplication. This yields a slightly
			 * worse entropy in the lower 8 bits, but that can be improved by adding:
			 *
			 * `i ^= i >> 6`
			 *
			 * For performance reasons, we avoid this improvement.
			 * */
			val i= scala.util.hashing.byteswap32(hcode)

			/* Jenkins hash
			 * for range 0-10000, output has the msb set to zero */
			// var h = hcode + (hcode << 12)
			// h ^= (h >> 22)
			// h += (h << 4)
			// h ^= (h >> 9)
			// h += (h << 10)
			// h ^= (h >> 2)
			// h += (h << 7)
			// h ^= (h >> 12)
			// h

			/* OLD VERSION
			 * quick, but bad for sequence 0-10000 - little entropy in higher bits
			 * since 2003 */
			// var h: Int = hcode + ~(hcode << 9)
			// h = h ^ (h >>> 14)
			// h = h + (h << 4)
			// h ^ (h >>> 10)

			// the rest of the computation is due to SI-5293
			val rotation = seed % 32
			val rotated = (i >>> rotation) | (i << (32 - rotation))
			rotated
		}
	}

	/**
	 * Returns a power of two >= `target`.
	 */
	private[traditional] def powerOfTwo(target: Int): Int = {
	/* See http://bits.stephan-brumme.com/roundUpToNextPowerOfTwo.html */
	var c = target - 1
	c |= c >>>  1
	c |= c >>>  2
	c |= c >>>  4
	c |= c >>>  8
	c |= c >>> 16
	c + 1
	}
	
	private def arrayString[E](a:Array[Ref[E]], eStr:String):String = s"array[$eStr](${a.map(_.single()).mkString(", ")})"

	class Contents[A, Entry >: Null <: HashEntry[A, Entry]](val loadFactor: Int, val table: Array[Ref[Entry]], val tableSize: Int, 
			val threshold: Int, val seedvalue: Int, val sizemap: Array[Ref[Int]]) {
		private[traditional] def debugInformation = List("Hash table contents",
				"-------------------",
				s"Table: ${arrayString(table, "Entry")}",
				s"Table size: $tableSize",
				s"Load factor: $loadFactor",
				s"Seedvalue: $seedvalue",
				s"Threshold: $threshold",
				s"Sizemap: ${arrayString(sizemap, "Int")}").map(l => "$l\n").mkString("")
	}

}

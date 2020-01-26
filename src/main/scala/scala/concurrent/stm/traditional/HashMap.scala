package scala.concurrent.stm.traditional

import scala.concurrent.stm._
import scala.annotation.tailrec

/** This class implements mutable maps using a hashtable.
 *
 *  @since 1
 *  @see [[http://docs.scala-lang.org/overviews/collections/concrete-mutable-collection-classes.html#hash_tables "Scala's Collection Library overview"]]
 *  section on `Hash Tables` for more information.
 *
 * o @tparam A    the type of the keys contained in this hash map.
 *  @tparam B    the type of the values assigned to keys in this hash map.
 *
 *  @define Coll `mutable.STMHashMap`
 *  @define coll mutable hash map
 *  @define thatinfo the class of the returned collection. In the standard library configuration,
 *    `That` is always `STMHashMap[A, B]` if the elements contained in the resulting collection are
 *    pairs of type `(A, B)`. This is because an implicit of type `CanBuildFrom[STMHashMap, (A, B), STMHashMap[A, B]]`
 *    is defined in object `STMHashMap`. Otherwise, `That` resolves to the most specific type that doesn't have
 *    to contain pairs of type `(A, B)`, which is `Iterable`.
 *  @define bfinfo an implicit value of class `CanBuildFrom` which determines the
 *    result class `That` from the current representation type `Repr`
 *    and the new element type `B`. This is usually the `canBuildFrom` value
 *    defined in object `STMHashMap`.
 *  @define mayNotTerminateInf
 *  @define willNotTerminateInf
 */

final class DefaultEntry[A,B](val key: A, initValue:B) extends HashEntry[A, DefaultEntry[A,B]]{
	val value:Ref[B] = Ref(initValue)
	override def toString = chainString()
	@tailrec def chainString(prev:String = ""):String = {
		val soFar = s"$prev (kv: $key, $value)"
		val tail = next.single()
		if(tail == null){
			soFar
		} else {
			chainString(s"$soFar ->")
		}
	}
}

class HashMap[A, B] private[traditional] (contents: HashTable.Contents[A, DefaultEntry[A, B]])
extends HashTable[A] {
	def this() = this(null)

	override protected[this] type Entry = DefaultEntry[A, B]
	atomic{ implicit t => initWithContents(contents) }
	
	def size: Int = tableSize.single()
	def contains(key: A)(implicit txn: InTxn): Boolean = findEntry(key) != null
	def get(key: A)(implicit txn: InTxn): Option[B] = Option(findEntry(key)).map(_.value())
	def put(key: A, value: B)(implicit txn: InTxn): Option[B] = Option(findOrAddEntry(key, value)).map{e => 
		val v = e.value();e.value() = value;v
	}

	def remove(key: A)(implicit txn: InTxn): Option[B] = Option(removeEntry(key)).map(_.value())

	override protected def createNewEntry[B1](key: A, value: B1): Entry = {
		new Entry(key, value.asInstanceOf[B])
	}
}

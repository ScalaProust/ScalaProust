package scala.concurrent.stm.dummy

import scala.concurrent.stm.InTxn
import scala.collection.concurrent.TrieMap
import edu.brown.collection.concurrent.BraunHeap
import scala.concurrent.stm.stamp.ImplFactory
import scala.concurrent.stm.stamp.MapAdapterTrait
import scala.concurrent.stm.stamp.PriorityQueueAdapterTrait


object StampImpl extends ImplFactory {
	/** As seen from object NonTxnFactory, the missing signatures are as follows. 
	 *  For convenience, these are usable as stub implementations.
	 */
	 def allocMap[Key, Value]: MapAdapterTrait[Key,Value] = new MapAdapterTrait[Key, Value] {
		 val map = TrieMap[Key, Value]()
		 
		 def put(k:Key, v:Value)(implicit txn:InTxn):Option[Value] = map.put(k, v)
		 def get(k:Key)(implicit txn:InTxn):Option[Value] = map.get(k)
		 def remove(k:Key)(implicit txn:InTxn):Option[Value] = map.remove(k)
		 def contains(k:Key)(implicit txn:InTxn):Boolean = map.contains(k)
		 def size(implicit txn:InTxn):Int = map.size
	 }
	 def allocPQueue[Elem : Ordering]: PriorityQueueAdapterTrait[Elem] = new PriorityQueueAdapterTrait[Elem] {
		 val pq = BraunHeap[Elem]()
		 
		 def min(implicit txn: InTxn):Option[Elem] = pq.getMin
		 def removeMin(implicit txn: InTxn):Option[Elem] = pq.removeMin
		 def insert(value:Elem)(implicit txn: InTxn):Unit = pq.insert(value)
		 def contains(value:Elem)(implicit txn: InTxn):Boolean = pq.iterator.contains(value)
		 def size(implicit txn:InTxn):Int = pq.size
	 }

}

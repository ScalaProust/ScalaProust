package scala.concurrent.stm.dummy.juc

import scala.concurrent.stm.InTxn
import java.util.concurrent.{ConcurrentHashMap, PriorityBlockingQueue}
import scala.concurrent.stm.stamp.ImplFactory
import scala.concurrent.stm.stamp.MapAdapterTrait
import scala.concurrent.stm.stamp.PriorityQueueAdapterTrait

object StampImpl extends ImplFactory {
	/** As seen from object NonTxnFactory, the missing signatures are as follows. 
	 *  For convenience, these are usable as stub implementations.
	 */
	 def allocMap[Key, Value]: MapAdapterTrait[Key,Value] = new MapAdapterTrait[Key, Value] {
		 val map = new ConcurrentHashMap[Key, Value]()
		 
		 def put(k:Key, v:Value)(implicit txn:InTxn):Option[Value] = Option(map.put(k, v))
		 def get(k:Key)(implicit txn:InTxn):Option[Value] = Option(map.get(k))
		 def remove(k:Key)(implicit txn:InTxn):Option[Value] = Option(map.remove(k))
		 def contains(k:Key)(implicit txn:InTxn):Boolean = map.contains(k)
		 def size(implicit txn:InTxn):Int = map.size
	 }
	 def allocPQueue[Elem : Ordering]: PriorityQueueAdapterTrait[Elem] = new PriorityQueueAdapterTrait[Elem] {
		 val pq = new PriorityBlockingQueue(11, implicitly[Ordering[Elem]])
		 
		 def min(implicit txn: InTxn):Option[Elem] = Option(pq.peek)
		 def removeMin(implicit txn: InTxn):Option[Elem] = Option(pq.poll)
		 def insert(value:Elem)(implicit txn: InTxn):Unit = pq.put(value)
		 def contains(value:Elem)(implicit txn: InTxn):Boolean = pq.contains(value)
		 def size(implicit txn:InTxn):Int = pq.size
	 }

}
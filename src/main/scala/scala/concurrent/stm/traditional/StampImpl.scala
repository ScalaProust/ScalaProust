package scala.concurrent.stm.traditional

import scala.concurrent.stm._
import scala.concurrent.stm.stamp._

class MapAdapter[Key, Value](backing:HashMap[Key, Value]) extends MapAdapterTrait[Key, Value] {
	override def put(k:Key, v:Value)(implicit txn:InTxn):Option[Value] = backing.put(k, v)
	override def get(k:Key)(implicit txn:InTxn):Option[Value] = backing.get(k)
	override def remove(k:Key)(implicit txn:InTxn):Option[Value] = backing.remove(k)
	override def contains(k:Key)(implicit txn:InTxn):Boolean = backing.contains(k)
	override def size(implicit txn:InTxn):Int = backing.size
}

class PQueueAdapter[Elem : Ordering](backing:BraunHeap[Elem]) extends PriorityQueueAdapterTrait[Elem] {
	def min(implicit txn: InTxn):Option[Elem] = backing.getMin
	def removeMin(implicit txn: InTxn):Option[Elem] = backing.removeMin
	def insert(value:Elem)(implicit txn: InTxn):Unit = backing.insert(value)
	def contains(value:Elem)(implicit txn: InTxn):Boolean = backing.iterator.contains(value)
	def size(implicit txn:InTxn):Int = backing.size
}

object StampImpl extends ImplFactory{
	override def allocMap[Key, Value]:MapAdapterTrait[Key, Value] = new MapAdapter[Key, Value](new HashMap[Key, Value])
	override def allocPQueue[Elem : Ordering]:PriorityQueueAdapterTrait[Elem] = new PQueueAdapter(BraunHeap())
}

package scala.concurrent.stm.predication

import scala.concurrent.stm._

import scala.concurrent.stm.stamp._
// Predication Data Structure
import scala.concurrent.stm.{TMap}
// Map Adaptor
import scala.concurrent.stm.stamp.{MapAdapterTrait}

class MapAdapter[Key, Value](backing:TMap[Key, Value]) extends MapAdapterTrait[Key, Value] {
	override def put(k:Key, v:Value)(implicit txn:InTxn):Option[Value] = backing.put(k, v)
	override def get(k:Key)(implicit txn:InTxn):Option[Value] = backing.get(k)
	override def remove(k:Key)(implicit txn:InTxn):Option[Value] = backing.remove(k)
	override def contains(k:Key)(implicit txn:InTxn):Boolean = backing.contains(k)
	override def size(implicit txn:InTxn):Int = backing.size
}

object StampImpl extends ImplFactory{
	override def allocMap[Key, Value]:MapAdapterTrait[Key, Value] = new MapAdapter[Key, Value](TMap[Key, Value]())
	def allocPQueue[Elem : Ordering]:PriorityQueueAdapterTrait[Elem] = throw new UnsupportedOperationException("No predication-implementation of a priority queue")
	
}



package scala.concurrent.stm.boosting

import scala.concurrent.stm.boosting.locks.{AbstractLock,LockAllocatorPolicy,UpdateStrategy}
import scala.concurrent.stm._

object PQueueTrait {
	sealed trait PQueueState
	case object PQueueMin extends PQueueState
	case object PQueueMultiSet extends PQueueState
}

trait PQueueTrait[V] extends ProustCollection {
	type AbstractState = PQueueTrait.PQueueState
	def insert(v : V)(implicit txn:InTxn) : Unit
	def min(implicit txn:InTxn):Option[V]
	def contains(v : V)(implicit txn:InTxn) : Boolean
	def removeMin(implicit txn:InTxn) : Option[V]
}
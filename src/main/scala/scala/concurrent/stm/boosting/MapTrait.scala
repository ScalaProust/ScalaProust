package scala.concurrent.stm.boosting

import scala.concurrent.stm.boosting.locks.{AbstractLock,LockAllocatorPolicy, UpdateStrategy}
import scala.concurrent.stm._

trait MapTrait[K, V] extends ProustCollection {
	type AbstractState = K
	def put(k : K, v : V)(implicit txn:InTxn) : Option[V]
	def get(k : K)(implicit txn:InTxn) : Option[V]
	def contains(k : K)(implicit txn:InTxn) : Boolean
	def remove(k : K)(implicit txn:InTxn) : Option[V]
}
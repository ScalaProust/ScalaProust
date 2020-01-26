package scala.concurrent.stm.stamp

import scala.concurrent.stm.InTxn

abstract class PriorityQueueAdapterTrait[E : Ordering] {
	def min(implicit txn: InTxn):Option[E]
	def removeMin(implicit txn: InTxn):Option[E]
	def insert(value:E)(implicit txn: InTxn):Unit
	def contains(value:E)(implicit txn: InTxn):Boolean
	def size(implicit txn:InTxn):Int
}
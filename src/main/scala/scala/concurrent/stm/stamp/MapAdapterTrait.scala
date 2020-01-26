package scala.concurrent.stm.stamp

import scala.concurrent.stm.InTxn

trait MapAdapterTrait[Key, Value] {
	def put(k:Key, v:Value)(implicit txn:InTxn):Option[Value]
	def get(k:Key)(implicit txn:InTxn):Option[Value]
	def remove(k:Key)(implicit txn:InTxn):Option[Value]
	def contains(k:Key)(implicit txn:InTxn):Boolean
	def size(implicit txn:InTxn):Int
}
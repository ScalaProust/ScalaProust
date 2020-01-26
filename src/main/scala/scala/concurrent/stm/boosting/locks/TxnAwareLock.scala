package scala.concurrent.stm.boosting.locks

import scala.concurrent.stm.{Txn,InTxnEnd,InTxn,MaybeTxn,NestingLevel}

object TxnAwareLock {
	sealed trait LockMode
	case object Pessimistic extends LockMode {
		override def toString:String = "Pessimistic"
	}
	case object Optimistic extends LockMode {
		override def toString:String = "Optimistic"
	}
}

trait TxnAwareLock {
	// diagnostic helpers
	type OwnerType = NestingLevel
	def owner(implicit maybeTxn:MaybeTxn):Option[OwnerType]
	def mode:TxnAwareLock.LockMode
	
	// The actual functionality
	def lock(write:Boolean)(implicit txn:InTxn):TxnAwareLock
	protected def unlock:Unit
}
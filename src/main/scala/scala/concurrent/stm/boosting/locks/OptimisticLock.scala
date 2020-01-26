package scala.concurrent.stm.boosting.locks

import scala.concurrent.stm._

class OptimisticLock extends TxnAwareLock {
	override val mode = TxnAwareLock.Optimistic
	
	private val lastWriter = Ref[Option[NestingLevel]](None)
	
	override def owner(implicit maybeTxn:MaybeTxn):Option[OwnerType] = lastWriter.single()
	
	override def lock(write:Boolean)(implicit txn:InTxn):OptimisticLock = {
		if(write){
			lastWriter() = Some(txn.rootLevel)
		} else {
			lastWriter() // Discarding the value here, but the side effect matters
		}
		this
	}
	
	override protected def unlock:Unit = throw new UnsupportedOperationException("There's never a reason to do this")
}
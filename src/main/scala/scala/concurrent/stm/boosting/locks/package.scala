package scala.concurrent.stm.boosting

import scala.concurrent.stm._

package object locks {
	def withTxnPath[Z](txnPath:InTxn => Z)(fallback: => Z)(implicit maybeTxn:MaybeTxn = TxnUnknown):Z = Txn.findCurrent.map(txnPath).getOrElse(fallback)
	def timeoutHere(implicit maybeTxn:MaybeTxn = TxnUnknown):Option[Long] = withTxnPath{implicit txn => NestingLevel.current.executor.retryTimeoutNanos}(None)
  
  	sealed trait UpdateStrategy
	case object Lazy extends UpdateStrategy
	case object Eager extends UpdateStrategy
}
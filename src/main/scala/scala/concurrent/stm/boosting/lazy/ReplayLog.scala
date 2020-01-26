package scala.concurrent.stm.boosting.`lazy`

import scala.concurrent.stm._
import scala.collection.mutable.Queue

object ReplayLog {
	def construct[DataType](initialize:DataType => ReplayLog[DataType], withData:DataType):TxnLocal[ReplayLog[DataType]] = {
		def tl:TxnLocal[ReplayLog[DataType]] = TxnLocal(init = initialize(withData), 
				whileCommitting = {implicit txn => if(tl.isInitialized){ tl().replay }})
		tl
	}
	
	def readOnly[DataType,Z](tl:TxnLocal[ReplayLog[DataType]], f:DataType => Z, d:DataType)(implicit txn:InTxn):Z = {
		if(tl.isInitialized){
			val log = tl()
			log()= f
		} else {
			f(d)
		}
	}
}

trait ReplayLog[DataType] {
	def wrapped:DataType
	def log:TraversableOnce[DataType => Any]
	def apply[Z](f:DataType => Z): Z
	def update[Z](f:DataType => Z): Z
	def replay(implicit txn:InTxnEnd) = log.foreach(_(wrapped))
}
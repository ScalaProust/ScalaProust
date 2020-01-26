package scala.concurrent.stm.boosting

import scala.concurrent.stm._

object CounterTrait {
	sealed trait CounterState
	case object CounterZero extends CounterState
}

trait CounterTrait extends ProustBase {
	type AbstractState = CounterTrait.CounterState
	
	def increment(implicit txn:InTxn):Unit
	def decrement(implicit txn:InTxn):Unit
	def zero(implicit txn:InTxn):Boolean
	def reset(implicit txn:InTxn):Unit
}
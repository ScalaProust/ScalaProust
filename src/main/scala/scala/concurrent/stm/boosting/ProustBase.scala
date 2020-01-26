package scala.concurrent.stm.boosting

import scala.concurrent.stm.boosting.locks.{AbstractLock,LockAllocatorPolicy, UpdateStrategy}
import scala.concurrent.stm.{InTxn,Ref}

trait ProustBase {
	type AbstractState
	
	protected def lAP:LockAllocatorPolicy[AbstractState]
	protected def uStrat:UpdateStrategy
	
	protected val abstractLock = new AbstractLock(lAP, uStrat)
}

object ProustCollection {
	private[ProustCollection] var keepSize:Boolean = true
	
	// This is horrible and should have safe-guards around it in real code,
	// but is easier than doing it the right way on short notice.
	def enableSizes:Unit = keepSize = true
	def disableSizes:Unit = keepSize = false
	
}

trait ProustCollection extends ProustBase {
	import ProustCollection.keepSize
	
	protected val committedSize = if(keepSize) {
		Ref(0).single
	} else {
		null
	}
	
	@inline def hasSize:Boolean = committedSize != null
	
	def size(implicit txn:InTxn):Int = if(hasSize) {
		committedSize()
	} else {
		throw new UnsupportedOperationException
	}
}
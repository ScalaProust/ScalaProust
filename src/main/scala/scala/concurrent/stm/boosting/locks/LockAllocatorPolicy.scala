package scala.concurrent.stm.boosting.locks

trait LockAllocatorPolicy[Key] {
	def freshLockForKey(key:Key):TxnAwareLock
	def mode:TxnAwareLock.LockMode
}

class OptimisticLockAllocatorPolicy[Key] extends LockAllocatorPolicy[Key] {
	import TxnAwareLock.{LockMode, Optimistic}
	/* Most AbstractLocks will want this */
	 def freshLockForKey(key:Key):TxnAwareLock = new OptimisticLock
	 def mode:LockMode = Optimistic
}

class PessimisticLockAllocatorPolicy[Key] extends LockAllocatorPolicy[Key] {
	import TxnAwareLock.{LockMode, Pessimistic}
	/* Most AbstractLocks will want this */
	 def freshLockForKey(key:Key):TxnAwareLock = new PessimisticLock
	 def mode:LockMode = Pessimistic
}
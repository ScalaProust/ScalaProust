package scala.concurrent.stm.boosting.locks

import scala.language.implicitConversions

import scala.concurrent._
import scala.concurrent.stm._
import scala.concurrent.stm.ccstm.WakeupManager.blocking

import java.util.concurrent.TimeUnit

class AbstractLock[Key](lockAllocator:LockAllocatorPolicy[Key], writeStrategy:UpdateStrategy) {
	import TxnAwareLock.{LockMode, Optimistic, Pessimistic}
	
	object LockFor {
		def unapply(ok:LockFor):Option[(Key, Boolean)] = Some((ok.key, ok.write))
	}
	sealed trait LockFor{
		def key:Key; def write:Boolean
	}
	case class Write(override val key:Key) extends LockFor{override val write = true}
	
	object Read {
		def unapply(r:Read):Option[Key] = Some(r.key)
	}
	implicit class Read(override val key:Key) extends LockFor{override val write = false}
	
	// TODO : Ensure that the Scala library version is at least 2.11.6 or TrieMap has atomicity problems with getOrElseUpdate
	private val locks = collection.concurrent.TrieMap[Key, TxnAwareLock]()
	
	def apply[Z](acquire:LockFor*)(f: =>Z)(invF:Z=>Unit = null)(implicit txn:InTxn):Z = {
		// (1) getOrElseUpdate must be atomic. Older versions of Scala don't have that guarantee!
		val acquired = (if(lockAllocator.mode == Optimistic) {
				acquire
			} else {
				// Process exclusive locks first, because waiting on those is more probable
				acquire.sortWith{case (l1, l2) => (l1.write && !l2.write) || (l1.key.hashCode < l2.key.hashCode)}
			}).map{res => locks.getOrElseUpdate(res.key, lockAllocator.freshLockForKey(res.key)).lock(res.write)} 
		val ret = f
		if((lockAllocator.mode == Optimistic) && (writeStrategy == Lazy)){
			/*
			 *  Reacquire all the locks as read-locks to ensure opacity.
			 *  This will force an abort if a commit occurred between
			 *  the initial acquire and the actual read from the data structure
			 *  thus preventing the inconsistent state from leaking out of this context.
			 *  
			 *  Any STM which will maintain opacity for Opt/Eager doesn't need this anyway
			 *  No STM which would otherwise fail opacity for Opt/Eager will benefit from it
			 */
			acquired.foreach{_.lock(false)}
		}
		if(invF != null) Txn.afterRollback(status => invF(ret))
		ret
	}
}
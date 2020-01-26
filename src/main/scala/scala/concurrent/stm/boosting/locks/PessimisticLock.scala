package scala.concurrent.stm.boosting.locks

import java.util.concurrent.{TimeUnit}
import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}
import scala.concurrent.stm.{InTxn, InTxnEnd, NestingLevel, Txn, MaybeTxn, TxnUnknown, atomic, TxnLocal}
import scala.collection.concurrent.{TrieMap => CTrieMap}
import scala.annotation._


class PessimisticLock extends TxnAwareLock {
	override val mode = TxnAwareLock.Pessimistic
	
	type OwnerPair = (Thread, OwnerType)
	type OwnerSetType = CTrieMap[Thread,OwnerType]
	type RWOwnerType = Either[OwnerSetType, OwnerPair]
	
	private val theReaders = Left(CTrieMap[Thread,OwnerType]())
	private def UNOWNED = {
		theReaders.left.get.clear
		theReaders
	}
	private val ownerRef:AtomicReference[RWOwnerType] = new AtomicReference(UNOWNED)
	private val writersWaiting = new AtomicInteger(0)
	
	/***********************************************
	 * Transactions will handle auto-unlocking in the following manner:
	 * - Every time we acquire a lock for the first time, we check if we're in a Txn context
	 * and if so, we also initialize an afterComplete hook to unlock
	 ***********************************************/
	private def markFirstAcquire(implicit txn:InTxn):Unit = {
		Txn.afterCompletion { status => unlock}
	}
	
	override protected def unlock:Unit = {
		val current = Thread.currentThread
		// No synchronize needed here - we're only testing against ourselves.
		ownerRef.get match {
			case Left(readers) if readers.contains(current) =>
				readers -= current
			case Right(owner) if owner._1 == current => ownerRef.set(UNOWNED)
			case notUs => throw new IllegalMonitorStateException(s"${Thread.currentThread} can't unlock this: it's owned by $notUs")
		}
	}
	
	override def lock(write:Boolean)(implicit txn:InTxn): PessimisticLock = {
		if(write){
			writersWaiting.incrementAndGet
		}
		while(!tryLock(write)){
			val maybeBlockingTxn = ownerRef.get.fold(_.readOnlySnapshot.values.headOption,{pair => Some(pair._2)})
			maybeBlockingTxn.foreach{txn.currentLevel.awaitCompletionOf(_, this)}
		}
		if(write){
			writersWaiting.decrementAndGet
		}
		this
	}

	@inline private final def targetOwner(implicit txn:InTxnEnd):OwnerPair = (Thread.currentThread, txn.rootLevel)
	
	def tryLock(write:Boolean)(implicit txn:InTxn):Boolean = {
		val current = targetOwner
		val amReading = ownerRef.get.fold({_.contains(current._1)},{owner => false})
		if(write){
			synchronized{
				ownerRef.get.fold({readers =>
					val numReaders = readers.size
					val ret = if(numReaders == 0){
						markFirstAcquire
						true
					} else if(numReaders == 1 && amReading){
						true
					} else {
						false
					}
					if(ret){
						ownerRef.set(Right(current))
					}
					ret
				},{_._1 == current._1})
			}
		} else {
			synchronized{
				ownerRef.get.fold({readers => 
					val ret = (amReading || writersWaiting.get == 0)
					if(ret && !amReading){
						markFirstAcquire
						readers += current
					}
					ret
				}, {_._1 == current._1})			
			}
		}
	}
	
	override def owner(implicit maybeTxn:MaybeTxn = TxnUnknown) = ownerRef.get.fold(lI => None, rO => Some(rO._2))
	override def toString = synchronized { s"${super.toString} [${ownerRef.get}]" }
}

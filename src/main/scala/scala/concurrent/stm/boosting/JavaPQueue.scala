package scala.concurrent.stm.boosting

import java.util.concurrent.PriorityBlockingQueue

import scala.concurrent.stm._
import scala.concurrent.stm.boosting.locks._

class JavaPQueue[Value : Ordering](initialCapacity:Int, override protected val lAP:LockAllocatorPolicy[PQueueTrait.PQueueState] = new OptimisticLockAllocatorPolicy[PQueueTrait.PQueueState]) extends PQueueTrait[Value] {
	
	// 11 is the default capacity for PriorityBlockingQueue, according to JavaDocs
	def this(lAP:LockAllocatorPolicy[PQueueTrait.PQueueState]) = this(11, lAP)
	
	import PQueueTrait.{PQueueMin, PQueueMultiSet}
	import abstractLock.{Read, Write}
	override protected val uStrat = Eager
	
	implicit class LazyDeletion(val value:Value) extends math.Ordered[LazyDeletion]{
		private var deletionState = false
		def deleted:Boolean = deletionState
		def delete:Unit = { deletionState = true }
		
		def compare(other:LazyDeletion):Int = implicitly[Ordering[Value]].compare(this.value, other.value)
		override def equals(o:Any) = try {
			val other = o.asInstanceOf[LazyDeletion]
			(this.deletionState == other.deletionState) && implicitly[Ordering[Value]].equiv(this.value, other.value)
		} catch {
			case _:ClassCastException => false
		}
	}
	
	private val pQueue = new PriorityBlockingQueue[LazyDeletion](initialCapacity)
	
	def insert(v : Value)(implicit txn:InTxn) : Unit = {
		abstractLock(Write(PQueueMultiSet), 
				min.collect{ 
					case curM if v < curM => Write(PQueueMin)
				}.getOrElse{Read(PQueueMin)}){ // The read lock has already been implicitly acquired by calling min, but let's make it explicit
			val wrapper:LazyDeletion = v
			pQueue.add(wrapper)
			if(hasSize) { committedSize() += 1 }
			wrapper
		}{_.delete}
	}
	
	def contains(v : Value)(implicit txn:InTxn) : Boolean = abstractLock(PQueueMultiSet){pQueue.contains(new LazyDeletion(v))}()
	
	/* 
	 * This does not change the abstract state of the data structure, 
	 * Return value: the smallest element which wasn't deleted
	 */
	@annotation.tailrec
	private def forceDeletions:Option[LazyDeletion] = {
		// this peek-and-maybe-take must be atomic
		// But if synchronize the whole method, we're imposing unnecessary serialization
		// this means multiple threads can forceDeletions in parallel, and they should all converge
		// on the same value for ret if the abstract operations are read-only
		val maybeRet = pQueue.synchronized {
			val maybeHead = pQueue.peek
			if(maybeHead == null || !maybeHead.deleted){
				Some(Option(maybeHead))
			} else{
				pQueue.take // This should never block since maybeHead is non-null
				None
			}
		}
		// This isn't idiomatic Scala, but it allows us to compile the tailrec
		maybeRet match {
			case Some(ret) => ret
			case None => forceDeletions
		}
	}
		
	def min(implicit txn:InTxn):Option[Value] = abstractLock(PQueueMin){forceDeletions.map(_.value)}()
	def removeMin(implicit txn:InTxn):Option[Value] = abstractLock(Write(PQueueMin), Write(PQueueMultiSet)){ 
		val removed = forceDeletions.map(_.value)
		pQueue.poll /* Actually remove it */
		if(hasSize) { committedSize() -= 1 }
		removed
	}{
		_.foreach{ value =>
			val wrapper:LazyDeletion = value
			pQueue.put(wrapper)
		}
	}
	
}
package scala.concurrent.stm.boosting.`lazy`

import edu.brown.collection.concurrent.BraunHeap

import scala.concurrent.stm.boosting.PQueueTrait
import scala.concurrent.stm.boosting.locks._
import scala.concurrent.stm._

object PriorityQueue {
	def defaultReplay[E](bh:BraunHeap[E]):ReplayLog[BraunHeap[E]] = {
		new SnapshotReplay[BraunHeap[E]](bh, _.snapshot)
	}
}

class PriorityQueue[E]
(val lAP:LockAllocatorPolicy[PQueueTrait.PQueueState] = new OptimisticLockAllocatorPolicy[PQueueTrait.PQueueState],
	replayAlloc: BraunHeap[E] => ReplayLog[BraunHeap[E]] = PriorityQueue.defaultReplay[E] _)(implicit ord:Ordering[E])
	extends PQueueTrait[E]
		{

	def this(lAP:LockAllocatorPolicy[PQueueTrait.PQueueState])(implicit ord:Ordering[E]) = this(lAP, PriorityQueue.defaultReplay[E] _)
	
	import PQueueTrait.{PQueueMin, PQueueMultiSet}
	import ord.mkOrderingOps
	import abstractLock.{Read, Write}
	

	val uStrat = Eager
	
	private val pq = BraunHeap[E]()
	private val replays = ReplayLog.construct(replayAlloc, pq)
	private def readOnly[Z](f:BraunHeap[E] => Z)(implicit txn:InTxn) = ReplayLog.readOnly(replays, f, pq)
	
	override def insert(v : E)(implicit txn:InTxn) : Unit = abstractLock(Write(PQueueMultiSet),
			min.collect{ 
					case curM if v < curM => Write(PQueueMin)
				}.getOrElse{Read(PQueueMin)}){
		replays()() = _.insert(v)
		if(hasSize){ committedSize += 1 }
	}()
	
	override def min(implicit txn:InTxn):Option[E] = abstractLock(PQueueMin){
		readOnly(_.getMin)
	}()
	
	override def contains(v : E)(implicit txn:InTxn):Boolean = abstractLock(PQueueMultiSet){
		readOnly(_.iterator.contains(v))
	}()
	
	override def removeMin(implicit txn:InTxn) : Option[E] = abstractLock(Write(PQueueMin), Write(PQueueMultiSet)){
		val prevMin = (replays()() = _.removeMin)
		if(hasSize && prevMin.isDefined){
			committedSize -= 1
		}
		prevMin
	}()
}

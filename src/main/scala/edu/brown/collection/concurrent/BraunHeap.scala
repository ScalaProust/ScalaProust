package edu.brown.collection.concurrent

import scala.annotation.tailrec

import twotails.mutualrec
import java.util.concurrent.locks.{Lock,ReentrantReadWriteLock}

object BraunHeap{
	import ReentrantReadWriteLock.{WriteLock, ReadLock}
	@inline private[BraunHeap] def throughLock[N, Z](fL:N => Lock)(fZ:N => Z)(n:N):Z = {
		val l = fL(n); l.lock;	val r = fZ(n); l.unlock; r
	}
	
	@inline private[BraunHeap] def withNullPath[E : Ordering,Z](v:HeapNode[E], nP: => Z, nnP: HeapNode[E] => Z):Z = {
		if(v == null) nP
		else nnP(v)
	}
	
	@inline private[BraunHeap] def toStringIfNotNull[E : Ordering](n:HeapNode[E]):String = {
		withNullPath[E,String](n,"()",{node => node.busyRead.lock; node.toString})
	}
	
	@inline private[BraunHeap] def dfsIfNotNull[E : Ordering](n:HeapNode[E]):Stream[E] = {
		withNullPath[E,Stream[E]](n,Stream.Empty,{node => node.busyRead.lock; node.dfs})
	}
	
	@inline private[BraunHeap] def snapshotIfNotNull[E : Ordering](n:HeapNode[E]):HeapNode[E] = {
		withNullPath[E,HeapNode[E]](n, null, _.snapshot)
	}
	
	def lockPair:(WriteLock, ReadLock) = {
		val r = new ReentrantReadWriteLock; (r.writeLock, r.readLock)
	}
	
	private[BraunHeap] class HeapNode[E](initValue:E, initLeft:HeapNode[E] = null, initRight:HeapNode[E] = null)(implicit ord: Ordering[E]) {
		import ord.mkOrderingOps
		@volatile private[BraunHeap] var value:E = initValue
		@volatile private[BraunHeap] var left:HeapNode[E] = initLeft
		@volatile private[BraunHeap] var right:HeapNode[E] = initRight
		@volatile private[BraunHeap] var snapCount = 0
		private[BraunHeap] val (busyWrite, busyRead) = lockPair
		
		def snapshot:HeapNode[E] = {
			busyWrite.lock
			snapCount += 1
			busyWrite.unlock
			this
		}
		
		private def unsnapHelper:HeapNode[E] = {
			if(snapCount > 0){
				val v = value
				val nL = snapshotIfNotNull(left)
				val nR = snapshotIfNotNull(right)
				snapCount -= 1
				val ret = new HeapNode(v, nL, nR)
				ret.busyWrite.lock
				busyWrite.unlock
				ret
			} else { this }
		}
		private[BraunHeap] def unsnap:HeapNode[E] = {
			assert(busyWrite.isHeldByCurrentThread)
			throughLock[HeapNode[E],HeapNode[E]](_.busyRead)(_.unsnapHelper)(this)
		}
		
		private def updateHelper(nV:E, nL:HeapNode[E], nR:HeapNode[E]):HeapNode[E] = {
			value = nV; left = nL; right = nR
			this
		}
		def update(newValue:E = value, newLeft:HeapNode[E] = left, newRight:HeapNode[E] = right):HeapNode[E] = {
			assert(busyWrite.isHeldByCurrentThread)
			unsnap.updateHelper(newValue, newLeft, newRight)
		}
		
		// INVARIANT: Is never called unless lock.isHeldByCurrentThread
		/*@mutualrec*/ private[BraunHeap] final def insertHelper(newValue:E):Unit = {
			assert(busyWrite.isHeldByCurrentThread)
			val (smaller, larger) = if(newValue < value){ (newValue, value) } else { (value, newValue) }
			val insertNeeded = (right != null)
			update(smaller, ensureWriteable(right, right_=, larger), left).insertHelperPost(insertNeeded, larger)
		}
		
		// INVARIANT: Is never called unless lock.isHeldByCurrentThread
		/*@mutualrec*/ private[BraunHeap] final def insertHelperPost(insertNeeded:Boolean, larger:E):Unit = {
			assert(busyWrite.isHeldByCurrentThread)
			if(insertNeeded){
				val leftNow = left
				busyWrite.unlock
				leftNow.insertHelper(larger)			
			} else {
				busyWrite.unlock
			}
		}
		
		// INVARIANT: Is never called unless lock.isHeldByCurrentThread
		// INVARIANT: Is never called except by synchronization on the root, 
		// so reaching back upwards to delete ourselves is fine.
		// N.B. We still need to acquire locks going down, because 
		// (a) other processes may not have finished their updates to this heap
		// (b) other processes may be simultaneously editing a snapshot with whom we still need to fork links correctly
		/*@mutualrec*/ private[BraunHeap] final def pullupLeftHelper(selfRef:HeapNode[E]=>Unit, amRoot:Boolean = false):Option[E] = {
			assert(busyWrite.isHeldByCurrentThread)
			// Simple O(log n) traversal left,
			// while swapping pairs to maintain shape
			withNullPath[E,Option[E]](left,{ // We are the left-most leaf
				selfRef(null)
				if(snapCount > 0) {
					// Silly edge case that could throw off our reference counting: 
					// we aren't modifying this node, just forgetting about it
					// so we can unlink from the snaps without copying first
					snapCount -= 1
				}
				busyWrite.unlock
				if(amRoot){
					busyWrite.unlock // We won't be using the second lock since we've been deleted
					None
				} else {
					Some(value)
				}
			},update(value, right, _).pullupLeftHelperPost(selfRef))
		}
		
		/*@mutualrec*/ private[BraunHeap] final def pullupLeftHelperPost(selfRef:HeapNode[E]=>Unit):Option[E] = {
			assert(busyWrite.isHeldByCurrentThread)
			selfRef(this)
			right.busyWrite.lock
			// asymmetry with insertHelperPost: we don't need rightNow because the root is still locked
			// so we don't have to worry about someone fiddling behind our backs for the same reason
			// selfProxy assignment is fine
			busyWrite.unlock
			right.pullupLeftHelper(right_=)
		}
		
		// INVARIANT: Is never called unless lock.isHeldByCurrentThread
		// INVARIANT: Is never called unless we are not a snapshot.
		// This holds because pullupLeftHelper ensures writeable explicitly
		// And base-case (removeMin) hasn't released a lock since updating the root
		@tailrec private[BraunHeap] final def pushdownHelper:Unit = {
			assert(busyWrite.isHeldByCurrentThread && snapCount == 0)
			// No swapping is necessary because we aren't altering the shape at all
			val pushNext = if(right == null && left != null){
				// Our left child is a leaf
				val tmpLV = left.value
				if(tmpLV < value){
					left.busyWrite.lock
					left = left.update(value)
					left.busyWrite.unlock
					value = tmpLV
				}
				null
			} else if(right != null) {
				val (tmpV, tmpLV, tmpRV) = (value, left.value, right.value)
				
				if((tmpLV < tmpV) || (tmpRV < tmpV)){
					val (nV, nU, nP) = if(tmpLV < tmpRV) {
						(tmpLV, left_= _, left)
					} else {
						(tmpRV, right_= _, right)
					}
					value = nV
					nP.busyWrite.lock
					val ret = nP.update(tmpV)
					nU(ret); ret
				} else {
					null
				}
			} else { // We are a singleton root *or* something is horribly broken
					null
			}
			busyWrite.unlock
			if(pushNext != null) pushNext.pushdownHelper
		}
		
		// INVARIANT: Is only called on a snapshot through BraunHeap.toString, and that snapshot is never exposed, 
		// so nobody is following us down. But we might still be waiting on a mutating thread ahead of us to unlink
		override def toString:String = {
			busyRead.unlock
			val leftComponent = toStringIfNotNull(left)
			val rightComponent = toStringIfNotNull(right)
			
			val valSnap = value
			s"($valSnap $leftComponent $rightComponent)"
		}
		
		// INVARIANT: Is only called on a snapshot through BraunHeap.iterator, and that snapshot is never exposed, 
		// so nobody is following us down. But we might still be waiting on a mutating thread ahead of us to unlink
		private[BraunHeap] def dfs:Stream[E] = {
			busyRead.unlock
			((value	#::	dfsIfNotNull(left))
					#::: dfsIfNotNull(right))
		}
		
	}

	def apply[E : Ordering](elems:E*):BraunHeap[E] = {
		val ret = new BraunHeap[E]
		elems.foreach(ret.insert)
		ret
	}
	
	@inline private[BraunHeap] def ensureWriteable[E : Ordering](init:HeapNode[E],mut:HeapNode[E]=>Unit, initialize:E):HeapNode[E] = {
		val ret = withNullPath(init,new HeapNode(initialize),{
			concrete:HeapNode[E] =>
				concrete.busyWrite.lock
				concrete.unsnap
		}); mut(ret); ret
	}
}

class BraunHeap[E : Ordering] private (initRoot:BraunHeap.HeapNode[E] = null) extends PQueue[E] {
	import BraunHeap._
	@volatile private var root:HeapNode[E] = initRoot
	private val (busyWrite, busyRead) = lockPair

	def syncReadRoot:HeapNode[E] = throughLock[BraunHeap[E],HeapNode[E]](_.busyRead)(_.root)(this)
	
	def snapshot:BraunHeap[E] = {
		new BraunHeap[E](snapshotIfNotNull(syncReadRoot))
	}
	
	def iterator:Iterator[E] = dfsIfNotNull(snapshot.root).iterator
	
	def getMin:Option[E] = {
		Option(syncReadRoot).map{throughLock[HeapNode[E],E](_.busyRead)(_.value) _}
	}
	
	private def insertPre(value:E):Boolean = {
		val insertNeeded = (root != null)
		ensureWriteable(root, root_=, value)
		insertNeeded
	}
	def insert(value:E):Unit =  {
		if(throughLock[BraunHeap[E],Boolean](_.busyWrite){
			_.insertPre(value)
		}(this)){ root.insertHelper(value) }
	}
	
	private def removeMinPre:(Option[E], Option[E]) = {
		withNullPath(root,(None, None),{
				rootInit:HeapNode[E] =>
					rootInit.busyWrite.lock
					root = rootInit.unsnap
					root.busyWrite.lock // Don't want anyone else to get the root between de-synchronizing and pushing down
										// So we'll lock again re-entrantly.
					val (oldRoot, newRoot) = (Some(root.value), root.pullupLeftHelper(root_=, true))
					(oldRoot, newRoot)
			})
	}
	def removeMin:Option[E] = {
		val (ret, newRoot) = throughLock[BraunHeap[E],(Option[E],Option[E])](_.busyWrite){
			_.removeMinPre
		}(this)
		
		if(ret.isDefined && newRoot.isDefined){
			root.value = newRoot.get
			root.pushdownHelper
		}
		ret
	}
	
	override def toString:String = {
		toStringIfNotNull(snapshot.root)

	}
}

package scala.concurrent.stm.traditional

import scala.annotation.tailrec
import scala.concurrent.stm.{InTxn, Ref, atomic}

object BraunHeap{
	@inline private[BraunHeap] def withNullPath[E : Ordering,Z](v:HeapNode[E], nP: => Z, nnP: HeapNode[E] => Z):Z = {
		if(v == null) nP
		else nnP(v)
	}
	
	@inline private[BraunHeap] def toStringIfNotNull[E : Ordering](n:HeapNode[E])(implicit txn:InTxn):String = {
		withNullPath[E,String](n,"()",_.toString)
	}
	
	@inline private[BraunHeap] def dfsIfNotNull[E : Ordering](n:HeapNode[E])(implicit txn:InTxn):Stream[E] = {
		withNullPath[E,Stream[E]](n,Stream.Empty,_.dfs)
	}
	
	@inline private[BraunHeap] def snapshotIfNotNull[E : Ordering](n:HeapNode[E])(implicit txn:InTxn):HeapNode[E] = {
		withNullPath[E,HeapNode[E]](n, null, _.snapshot)
	}
	
	private[BraunHeap] class HeapNode[E](initValue:E, initLeft:HeapNode[E] = null, initRight:HeapNode[E] = null)(implicit ord: Ordering[E]) {
		import ord.mkOrderingOps
		private[BraunHeap] val value:Ref[E] = Ref(initValue)
		private[BraunHeap] val left:Ref[HeapNode[E]] = Ref(initLeft)
		private[BraunHeap] val right:Ref[HeapNode[E]] = Ref(initRight)
		private[BraunHeap] val snapCount:Ref[Int] = Ref(0)
		
		def snapshot(implicit txn:InTxn):HeapNode[E] = {
			snapCount += 1
			this
		}
		
		private[BraunHeap] def unsnap(implicit txn:InTxn):HeapNode[E] = {
			if(snapCount() > 0){
				val v = value()
				val nL = snapshotIfNotNull(left())
				val nR = snapshotIfNotNull(right())
				snapCount -= 1
				val ret = new HeapNode(v, nL, nR)
				ret
			} else { this }
		}
		
		private def updateHelper(nV:E, nL:HeapNode[E], nR:HeapNode[E])(implicit txn:InTxn):HeapNode[E] = {
			value() = nV; left() = nL; right() = nR
			this
		}
		def update(newValue:E, newLeft:HeapNode[E], newRight:HeapNode[E])(implicit txn:InTxn):HeapNode[E] = {
			unsnap.updateHelper(newValue, newLeft, newRight)
		}
		def update(newValue:E)(implicit txn:InTxn):HeapNode[E] = {
			unsnap.updateHelper(newValue, left(), right())
		}
		
		private[BraunHeap] final def insertHelper(newValue:E)(implicit txn:InTxn):Unit = {
			val (smaller, larger) = if(newValue < value()){ (newValue, value()) } else { (value(), newValue) }
			val insertNeeded = (right() != null)
			update(smaller, ensureWriteable(right, larger), left()).insertHelperPost(insertNeeded, larger)
		}
		
		private final def insertHelperPost(insertNeeded:Boolean, larger:E)(implicit txn:InTxn):Unit = {
			if(insertNeeded){
				val leftNow = left
				leftNow().insertHelper(larger)			
			}
		}
		
		private[BraunHeap] final def pullupLeftHelper(selfRef:Ref[HeapNode[E]], amRoot:Boolean = false)(implicit txn:InTxn):Option[E] = {
			// Simple O(log n) traversal left,
			// while swapping pairs to maintain shape
			withNullPath[E,Option[E]](left(),{ // We are the left-most leaf
				selfRef() = null
				if(snapCount() > 0) {
					// Silly edge case that could throw off our reference counting: 
					// we aren't modifying this node, just forgetting about it
					// so we can unlink from the snaps without copying first
					snapCount -= 1
				}
				if(amRoot){
					None
				} else {
					Some(value())
				}
			},update(value(), right(), _).pullupLeftHelperPost(selfRef))
		}
		
		private final def pullupLeftHelperPost(selfRef:Ref[HeapNode[E]])(implicit txn:InTxn):Option[E] = {
			selfRef() = this
			right().pullupLeftHelper(right)
		}
		
		private[BraunHeap] final def pushdownHelper(implicit txn:InTxn):Unit = {
			// No swapping is necessary because we aren't altering the shape at all
			val pushNext = if(right() == null && left() != null){
				// Our left child is a leaf
				val tmpLV = left().value()
				if(tmpLV < value()){
					left() = left().update(value())
					value() = tmpLV
				}
				null
			} else if(right() != null) {
				val (tmpV, tmpLV, tmpRV) = (value(), left().value(), right().value())
				
				if((tmpLV < tmpV) || (tmpRV < tmpV)){
					val (nV, nU, nP) = if(tmpLV < tmpRV) {
						(tmpLV, left, left())
					} else {
						(tmpRV, right, right())
					}
					value() = nV
					val ret = nP.update(tmpV)
					nU() = ret; ret
				} else {
					null
				}
			} else { // We are a singleton root *or* something is horribly broken
					null
			}
			if(pushNext != null) pushNext.pushdownHelper
		}
		
		// INVARIANT: Is only called on a snapshot through BraunHeap.toString, and that snapshot is never exposed, 
		// so nobody is following us down. But we might still be waiting on a mutating thread ahead of us to unlink
		override def toString:String = {
			atomic { implicit txn =>
				val leftComponent = toStringIfNotNull(left())
				val rightComponent = toStringIfNotNull(right())
				
				val valSnap = value()
				s"($valSnap $leftComponent $rightComponent)"
			}
		}
		
		// INVARIANT: Is only called on a snapshot through BraunHeap.iterator, and that snapshot is never exposed, 
		// so nobody is following us down. But we might still be waiting on a mutating thread ahead of us to unlink
		private[BraunHeap] def dfs(implicit txn:InTxn):Stream[E] = {
			((value()	#::	dfsIfNotNull(left()))
						#:::dfsIfNotNull(right()))
		}
		
	}

	def apply[E : Ordering](elems:E*):BraunHeap[E] = {
		val ret = new BraunHeap[E]
		atomic {
			implicit txn =>	elems.foreach(ret.insert)
		}
		ret
	}
	
	@inline private[BraunHeap] def ensureWriteable[E : Ordering](ref:Ref[HeapNode[E]], initialize:E)(implicit txn:InTxn):HeapNode[E] = {
		val ret = withNullPath(ref(),new HeapNode(initialize),{
			concrete:HeapNode[E] =>
				concrete.unsnap
		}); ref()= ret; ret
	}
}


class BraunHeap[E : Ordering] private (initRoot:BraunHeap.HeapNode[E] = null) {
	import BraunHeap._
	
	private val root:Ref[HeapNode[E]] = Ref(initRoot)
	private val sizeRef:Ref[Int] = Ref(0)
	
	def size(implicit txn:InTxn):Int = sizeRef()
	
	def snapshot(implicit txn:InTxn):BraunHeap[E] = {
		new BraunHeap[E](snapshotIfNotNull(root()))
	}
	
	def iterator(implicit txn:InTxn):Iterator[E] = dfsIfNotNull(snapshot.root()).iterator
	
	def getMin(implicit txn:InTxn):Option[E] = {
		Option(root()).map{_.value()}
	}
	
	private def insertPre(value:E)(implicit txn:InTxn):Boolean = {
		val insertNeeded = (root() != null)
		ensureWriteable(root, value)
		insertNeeded
	}
	def insert(value:E)(implicit txn:InTxn):Unit =  {
		if(insertPre(value)){ root().insertHelper(value) }
		sizeRef += 1
	}
	
	private def removeMinPre(implicit txn:InTxn):(Option[E], Option[E]) = {
		withNullPath(root(),(None, None),{
				rootInit:HeapNode[E] =>
					root() = rootInit.unsnap
					val (oldRoot, newRoot) = (Some(root().value()), root().pullupLeftHelper(root, true))
					(oldRoot, newRoot)
			})
	}
	def removeMin(implicit txn:InTxn):Option[E] = {
		val (ret, newRoot) = removeMinPre
		
		if(ret.isDefined && newRoot.isDefined){
			root().value() = newRoot.get
			root().pushdownHelper
		}
		if(ret.isDefined) {
			sizeRef -= 1
		}
		ret
	}
	
	override def toString:String = {
		atomic {
			implicit txn =>
				toStringIfNotNull(snapshot.root())
		}

	}
}

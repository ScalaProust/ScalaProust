package scala.concurrent.stm.boosting.`lazy`

import scala.language.higherKinds

import scala.collection.concurrent.{TrieMap => RawTrieMap}

import scala.concurrent.stm._
import scala.concurrent.stm.boosting.locks._
import scala.concurrent.stm.boosting.MapTrait

class TrieMap[Key, Value](override protected val lAP:LockAllocatorPolicy[Key] = new OptimisticLockAllocatorPolicy[Key]) 
extends MapTrait[Key, Value] {
	import abstractLock.{Read, Write}
	override protected val uStrat = Eager
	
	private val map = RawTrieMap[Key, Value]()
	private val replays = ReplayLog.construct[RawTrieMap[Key,Value]](new SnapshotReplay(_, _.snapshot), map)
	private def readOnly[Z](f:RawTrieMap[Key,Value] => Z)(implicit txn:InTxn) = ReplayLog.readOnly(replays, f, map)
	
	def put(key: Key, value: Value)(implicit txn:InTxn): Option[Value] = abstractLock(Write(key)){
		val prevState = (replays()()= _.put(key, value))
		if(hasSize && prevState.isEmpty) committedSize() += 1
		prevState
	}()

	def get(key: Key)(implicit txn:InTxn): Option[Value] = abstractLock(key){ readOnly(_.get(key)) }()
	def contains(key: Key)(implicit txn:InTxn): Boolean = abstractLock(key){ readOnly(_.contains(key)) }()

	def remove(key: Key)(implicit txn:InTxn): Option[Value] = abstractLock(Write(key)) {
		val prevState = (replays()() = _.remove(key))
		if(hasSize && prevState.isDefined) committedSize() -= 1
		prevState
	}()
}

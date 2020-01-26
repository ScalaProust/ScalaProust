package scala.concurrent.stm.boosting.`lazy`

import scala.language.higherKinds
import scala.concurrent.stm._
import scala.concurrent.stm.boosting.MapTrait
import scala.concurrent.stm.boosting.locks._
import java.util.concurrent.ConcurrentHashMap
import java.util.{Map}


import scala.collection.JavaConverters._


object HashMap {
	def defaultReplay[K,V](m:Map[K,V]):ReplayLog[Map[K,V]] = {
		new SnapshotReplay[Map[K,V]](m, new Memoizer(_))
	}
}

class HashMap[Key, Value](val lAP:LockAllocatorPolicy[Key] = new OptimisticLockAllocatorPolicy[Key], replayAlloc: Map[Key,Value] => ReplayLog[Map[Key,Value]] = HashMap.defaultReplay _) 
extends MapTrait[Key, Value]{
	def this(lAP:LockAllocatorPolicy[Key]) = this(lAP, HashMap.defaultReplay _)
	
	import abstractLock.{Read, Write}
	val uStrat = Eager
	
	// TODO: Parameters set to be benchmark friendly.
	// Add a way to pass in sizing hints in StampImpl
	private val map = new ConcurrentHashMap[Key, Value](1024, 0.75f, 32)
	private val replays = ReplayLog.construct(replayAlloc, map)
	private def readOnly[Z](f:Map[Key,Value] => Z)(implicit txn:InTxn) = ReplayLog.readOnly(replays, f, map)
	
	def put(key: Key, value: Value)(implicit txn:InTxn): Option[Value] = abstractLock(Write(key)) {
		val prevState = (replays()() = _.put(key, value))
		if(hasSize && prevState == null) committedSize() += 1
		Option(prevState)	
	}()

	def get(key: Key)(implicit txn:InTxn): Option[Value] = abstractLock(key){ Option(readOnly(_.get(key))) }()
	def contains(key: Key)(implicit txn:InTxn): Boolean = abstractLock(key){ readOnly(_.containsKey(key)) }()

	def remove(key: Key)(implicit txn:InTxn): Option[Value] = abstractLock(Write(key)){
		val prevState = (replays()()= _.remove(key))
		if (hasSize && prevState != null) committedSize() -= 1
		Option(prevState)
	}()
}

object CombiningHashMap {
	implicit def chmMemoizer[K,V]:MemoizerFactory[Map[K,V],Memoizer[K,V]] = {
		new MemoizerFactory[Map[K,V],Memoizer[K,V]]{
			def apply(wrapped:Map[K,V]):Memoizer[K,V] = { new Memoizer(wrapped) }
		}
	}
	
	implicit def chmSynthLog[K,V]:CombiningReplayIteratorFromLocalCopy[Map[K,V],Memoizer[K,V]] = {
		new CombiningReplayIteratorFromLocalCopy[Map[K,V],Memoizer[K,V]]{
				private def put(k:K, v:V)(m:Map[K,V]):Unit = m.put(k,v)
				private def remove(k:K)(m:Map[K,V]):Unit = m.remove(k)
			
			def apply(localCopy:Memoizer[K,V]):Iterator[Map[K,V] => Any] = {
				localCopy.seqCache.entrySet.iterator.asScala.map{
					case entry => 
						val k = entry.getKey
						entry.getValue match {
							case Some(v) => put(k, v) _
							case None => remove(k) _
						}
				}
			}
		}
	}
	
	def defaultReplay[K,V](m:Map[K,V]) = {
		new CombiningReplay[Map[K,V],Memoizer[K,V]](m)
	}
}

class CombiningHashMap[Key, Value](lAP:LockAllocatorPolicy[Key] = new OptimisticLockAllocatorPolicy[Key]) extends HashMap[Key,Value](lAP, CombiningHashMap.defaultReplay _)
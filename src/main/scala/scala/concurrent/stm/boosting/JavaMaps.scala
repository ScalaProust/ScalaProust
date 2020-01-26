package scala.concurrent.stm.boosting

import scala.language.higherKinds

import scala.concurrent.stm._
import scala.concurrent.stm.boosting.locks._
import java.util.concurrent.{ConcurrentMap, ConcurrentHashMap, ConcurrentSkipListMap}

class JavaMap[Key, Value, R[Key, Value] <: ConcurrentMap[Key,Value]](map: R[Key, Value], override protected val lAP:LockAllocatorPolicy[Key] = new OptimisticLockAllocatorPolicy[Key]) 
extends MapTrait[Key, Value]{
	import abstractLock.{Read, Write}
	override protected val uStrat = Eager
	
	def put(key: Key, value: Value)(implicit txn:InTxn): Option[Value] = abstractLock(Write(key)) {
		val prevState = map.put(key, value)
		if(hasSize && prevState == null) committedSize() += 1
		Option(prevState)	
	}{
		_ match {
			case None => map.remove(key)
			case Some(oldValue) => map.put(key, oldValue)
		}
	}

	def get(key: Key)(implicit txn:InTxn): Option[Value] = abstractLock(key){Option(map.get(key))}()
	def contains(key: Key)(implicit txn:InTxn): Boolean = abstractLock(key){map.containsKey(key)}()

	def remove(key: Key)(implicit txn:InTxn): Option[Value] = abstractLock(Write(key)){
		val prevState = map.remove(key)
		if (hasSize && prevState != null) committedSize() -= 1
		Option(prevState)
	}{
		_.foreach{
			oldValue => map.put(key, oldValue)
		}
	}
}


class HashMap[Key, Value](lAP:LockAllocatorPolicy[Key] = new OptimisticLockAllocatorPolicy[Key]) extends JavaMap(new ConcurrentHashMap[Key, Value](), lAP)
class SkipListMap[Key, Value](lAP:LockAllocatorPolicy[Key] = new OptimisticLockAllocatorPolicy[Key]) extends JavaMap(new ConcurrentSkipListMap[Key, Value], lAP)

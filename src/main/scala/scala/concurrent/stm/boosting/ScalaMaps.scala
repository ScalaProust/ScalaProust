package scala.concurrent.stm.boosting


import scala.language.higherKinds

import scala.collection.concurrent.{TrieMap => RawTrieMap, Map}

import scala.concurrent.stm._
import scala.concurrent.stm.boosting.locks._

class ScalaMap[Key, Value, R[Key, Value] <: Map[Key,Value]](map: R[Key, Value], val lAP:LockAllocatorPolicy[Key] = new OptimisticLockAllocatorPolicy[Key]) 
extends MapTrait[Key, Value] {
	import abstractLock.{Read, Write}
	val uStrat = Eager
	
	def put(key: Key, value: Value)(implicit txn:InTxn): Option[Value] = abstractLock(Write(key)){
		val prevState = map.put(key, value)
		if(hasSize && prevState.isEmpty) committedSize() += 1
		prevState
	}{
		_ match {
			case Some(oldValue) => map.put(key, oldValue)
			case None => map.remove(key)
		}
	}

	def get(key: Key)(implicit txn:InTxn): Option[Value] = abstractLock(key){map.get(key)}()
	def contains(key: Key)(implicit txn:InTxn): Boolean = abstractLock(key){map.contains(key)}()

	def remove(key: Key)(implicit txn:InTxn): Option[Value] = abstractLock(Write(key)) {
		val prevState = map.remove(key)
		if(hasSize && prevState.isDefined) committedSize() -= 1
		prevState
	}{
		_.foreach{
			oldValue => map.put(key, oldValue)
		}
	}

}

class TrieMap[Key, Value](lAP:LockAllocatorPolicy[Key] = new OptimisticLockAllocatorPolicy[Key]) extends ScalaMap(RawTrieMap[Key, Value](), lAP)

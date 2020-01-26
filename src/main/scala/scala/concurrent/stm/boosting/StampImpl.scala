package scala.concurrent.stm.boosting

import scala.concurrent.stm.stamp._
import scala.concurrent.stm.InTxn

import scala.concurrent.stm.boosting.`lazy`.{TrieMap => LazyTrieMap, HashMap => LazyHashMap, CombiningHashMap, PriorityQueue => BHPQueue}
import scala.concurrent.stm.boosting.locks.{LockAllocatorPolicy,PessimisticLockAllocatorPolicy,OptimisticLockAllocatorPolicy,TxnAwareLock}

import scala.util.Try
import scala.reflect.runtime.universe.WeakTypeTag
import scala.language.higherKinds

class MapAdapter[Key, Value](backing:MapTrait[Key, Value]) extends MapAdapterTrait[Key, Value] {
	override def put(k:Key, v:Value)(implicit txn:InTxn):Option[Value] = backing.put(k, v)
	override def get(k:Key)(implicit txn:InTxn):Option[Value] = backing.get(k)
	override def remove(k:Key)(implicit txn:InTxn):Option[Value] = backing.remove(k)
	override def contains(k:Key)(implicit txn:InTxn):Boolean = backing.contains(k)
	override def size(implicit txn:InTxn):Int = backing.size
}

class PriorityQueueAdapter[V : Ordering](backing:PQueueTrait[V]) extends PriorityQueueAdapterTrait[V] {
	override def min(implicit txn:InTxn):Option[V] = backing.min
	override def contains(value: V)(implicit txn: InTxn): Boolean = backing.contains(value) 
	override def insert(value: V)(implicit txn: InTxn): Unit = backing.insert(value)
	override def removeMin(implicit txn: InTxn): Option[V] = backing.removeMin
	override def size(implicit txn: InTxn): Int = backing.size
}

object StampImpl extends ImplFactory{
	import TxnAwareLock.{LockMode,Pessimistic,Optimistic}
	
	private val lockModes = Set[LockMode](Pessimistic, Optimistic)
	private val defaultMode:LockMode = Optimistic
	private var modeInternal:LockMode = defaultMode
	
	private def makeLAP[K]:LockAllocatorPolicy[K] = modeInternal match {
		case Optimistic => new OptimisticLockAllocatorPolicy[K]
		case Pessimistic => new PessimisticLockAllocatorPolicy[K]
	}
	
	// This is super unsafe, but there isn't a good way to tie bonusArgTs to bonusArgs....
	private def reflectedNewWithLAP[T <: ProustBase](t:WeakTypeTag[_ >: T], bonusArgTs:Class[_]*)(bonusArgs:Object*):T = {
		val constructor = t.mirror.runtimeClass(t.tpe).getConstructor((classOf[LockAllocatorPolicy[T#AbstractState]] +: bonusArgTs):_*)
		constructor.newInstance((makeLAP[T#AbstractState] +: bonusArgs):_*).asInstanceOf[T]
	}
	
	private class MapImpl[+R[Key, Value] <: MapTrait[Key, Value]](implicit t:WeakTypeTag[R[_,_]]){
		def allocMap[Key, Value] = {
			new MapAdapter[Key, Value](reflectedNewWithLAP[R[Key,Value]](t)())
		}
	}
	
	private val mapImplSelect = Map[String,MapImpl[MapTrait]](
			"TrieMap" -> new MapImpl[TrieMap],
			"LazyTrieMap" -> new MapImpl[LazyTrieMap],
			"HashMap" -> new MapImpl[HashMap],
			"LazyHashMap" -> new MapImpl[LazyHashMap],
			"CombiningLazyHashMap" -> new MapImpl[CombiningHashMap],
			"SkipListMap" -> new MapImpl[SkipListMap]
			)
	
	private val defaultMapImpl = "TrieMap"
	private var mapImplInternal = mapImplSelect(defaultMapImpl)
	
	private val defaultNoSizes = false
	
	private class PQImpl[+R[Value] <: PQueueTrait[Value]](implicit t:WeakTypeTag[R[_]]){
		def allocPQueue[Value : Ordering]:PriorityQueueAdapterTrait[Value] = {
			new PriorityQueueAdapter[Value](reflectedNewWithLAP[R[Value]](t, classOf[Ordering[Value]])(implicitly[Ordering[Value]]))
		}
	}
	
	private val pqImplSelect = Map[String,PQImpl[PQueueTrait]](
			"BraunHeap" -> new PQImpl[BHPQueue],
			"PriorityBlockingQueue" -> new PQImpl[JavaPQueue]
			)
	private val defaultPQImpl = "BraunHeap"
	private var pqImplInternal = pqImplSelect(defaultPQImpl)
	
	override def parseArgs(argv:Array[String], i:Int):(Int,Int) = {
		val nextArg = argv(i+1)
		argv(i) match {
			case "-MV" => 
				mapImplInternal = mapImplSelect.getOrElse(nextArg, null)
				if(mapImplInternal == null){
					(1,1)
				} else {
					(2,0)
				}
			case "-PQV" =>
				pqImplInternal = pqImplSelect.getOrElse(nextArg, null)
				if(pqImplInternal == null){
					(1,1)
				} else {
					(2, 0)
				}
			case "-LM" =>
				lockModes.find{_.toString == nextArg}.map{
					newMode =>
						modeInternal = newMode
						(2,0)
				}.getOrElse((1,1))
			case "-NS" =>
				Try(nextArg.toBoolean).toOption.map{
					case noSizes =>
						if(noSizes){
							ProustCollection.disableSizes
						} else {
							ProustCollection.enableSizes
						}
						(2,0)
				}.getOrElse(1,1)
			case _ => (1,1)
		}
	}
	
	override def displayUsage = {
		Console.println(s"    MV <STR>    Choice of [m]ap [v]ariant             ($defaultMapImpl)")
		Console.println(s"                One of {${mapImplSelect.keySet.toList.sorted.mkString(", ")}}")
		Console.println(s"    PQV <STR>   Choice of [pq]ueue [v]ariant          ($defaultPQImpl)")
		Console.println(s"                One of {${pqImplSelect.keySet.toList.sorted.mkString(", ")}}")
		Console.println(s"    LM <STR>    Choice of [l]ock [m]ode               ($defaultMode)")
		Console.println(s"                One of {${lockModes.map(_.toString).toList.sorted.mkString(", ")}}")
		Console.println(s"    NS <BOOL>   Allocate containers with [n]o [s]izes ($defaultNoSizes)")
	}
	
	
	override def allocMap[Key, Value]:MapAdapterTrait[Key, Value] = mapImplInternal.allocMap
	override def allocPQueue[Elem : Ordering]:PriorityQueueAdapterTrait[Elem] = pqImplInternal.allocPQueue
}

package scala.concurrent.stm.boosting.`lazy`

import scala.concurrent.stm.boosting.CounterTrait
import scala.concurrent.stm.boosting.locks._
import scala.concurrent.stm._

import java.util.concurrent.atomic.AtomicInteger

object AtomicCounter {
	private[`lazy`] object safeAdjust extends java.util.function.IntBinaryOperator {
		def applyAsInt(prev:Int, inc:Int):Int = {
			if(prev >= -inc) {
				prev + inc
			} else {
				prev
			}
		}
	}
}

trait AtomicCounter {
	private[`lazy`] def value:Int
	
	def increment:Unit
	def decrement:Boolean
	def zero:Boolean
	def reset:Unit
	
	private[`lazy`] def safeAdjust(delta:Int):Boolean
}

object JACUAtomicCounter {
	def apply(init:Int):JACUAtomicCounter = new JACUAtomicCounter(init)
}

class JACUAtomicCounter(init:Int) extends AtomicCounter {
	private val counter:AtomicInteger = new AtomicInteger(init)
	
	override def increment:Unit = { counter.incrementAndGet }
	override def decrement:Boolean = {
		val old = counter.getAndAccumulate(-1, AtomicCounter.safeAdjust)
		(old > 0)
	}
	override def zero:Boolean = { counter.get == 0 }
	override def reset:Unit = { counter.set(0) }
	
	override def value:Int = counter.get
	
	def safeAdjust(delta:Int):Boolean = {
		val old = counter.getAndAccumulate(delta, AtomicCounter.safeAdjust)
		old + delta >= 0
	}
}

object CounterMemoizer {
	def apply(wrapped:JACUAtomicCounter):CounterMemoizer = new CounterMemoizer(wrapped)
}

class CounterMemoizer(wrapped:JACUAtomicCounter) extends AtomicCounter {
	private val snap:Int = wrapped.value
	private var delta:Int = 0
	
	override def increment:Unit = { delta += 1 }
	override def decrement:Boolean = {
		val canDecrement = (snap + delta > 0)
		if(canDecrement){
			delta -= 1
		}
		canDecrement
	}
	
	override def zero:Boolean = { (snap + delta) == 0 }
	override def reset:Unit = { delta = -snap }
	
	override def value:Int = { snap + delta }
	
	private[`lazy`] def apply():(JACUAtomicCounter => Unit) = if(delta != 0) {
		_.safeAdjust(delta)
	} else {
		{_:JACUAtomicCounter => }
	}
	
	def safeAdjust(delta:Int) = throw new UnsupportedOperationException
	
}

object Counter {
	
}

class Counter(val lAP:LockAllocatorPolicy[CounterTrait.CounterState] = new OptimisticLockAllocatorPolicy[CounterTrait.CounterState]) extends CounterTrait {
	import CounterTrait.{CounterZero}
	import abstractLock.{Read, Write}
	val uStrat = Lazy
	
	@volatile var counter:Int = 0
	
	def increment(implicit txn:InTxn):Unit = {
		
	}
	
	def decrement(implicit txn:InTxn):Unit = {
		
	}
	
	def zero(implicit txn:InTxn):Boolean = abstractLock(Read(CounterZero)) {
		true
	}()
	
	def reset(implicit txn:InTxn):Unit = abstractLock(Write(CounterZero)) {
		
	}()
}
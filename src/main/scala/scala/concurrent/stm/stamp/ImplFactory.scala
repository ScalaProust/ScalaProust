package scala.concurrent.stm.stamp

import scala.concurrent.stm.TxnExecutor

trait ImplFactory {
	def parseArgs(argv:Array[String], i:Int):(Int,Int) = (1,1)
	def displayUsage:Unit = Console.println("    This factory has no configuration options")
	def allocMap[Key, Value]:MapAdapterTrait[Key, Value]
	def allocMapArray[Key, Value](n:Int):Array[MapAdapterTrait[Key, Value]] = Array.fill(n)(null)
	
	def allocPQueue[Elem : Ordering]:PriorityQueueAdapterTrait[Elem]
	def allocPQueueArray[Elem : Ordering](n:Int):Array[PriorityQueueAdapterTrait[Elem]] = Array.fill(n)(null)
	
	def atomic:TxnExecutor = scala.concurrent.stm.atomic
}
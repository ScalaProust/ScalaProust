package edu.brown.collection.concurrent

abstract class PQueue[E : Ordering] extends Iterable[E] {
	def getMin:Option[E]
	def removeMin:Option[E]
	def insert(e:E):Unit
	
	def snapshot:PQueue[E]
}

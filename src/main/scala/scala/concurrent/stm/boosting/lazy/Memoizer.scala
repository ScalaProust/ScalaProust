package scala.concurrent.stm.boosting.`lazy`

import java.util.{Map,HashMap => SeqHashMap}

class Memoizer[Key, Value](wrapped:Map[Key, Value]) extends Map[Key, Value] {
	val seqCache = new SeqHashMap[Key, Option[Value]]
	
	def clear(): Unit =  throw new UnsupportedOperationException
	override def containsKey(k: Any): Boolean = {
		seqCache.get(k) match {
			case None => false
			case Some(_) => true
			case null => wrapped.containsKey(k)
		}
	}
	def containsValue(x$1: Any): Boolean = throw new UnsupportedOperationException
	def entrySet(): java.util.Set[java.util.Map.Entry[Key,Value]] = throw new UnsupportedOperationException
	override def get(k: Any): Value = {
		seqCache.get(k) match {
			case None => null.asInstanceOf[Value]
			case Some(v) => v
			case null => wrapped.get(k)
		}
	}
	def isEmpty(): Boolean =  throw new UnsupportedOperationException
	def keySet(): java.util.Set[Key] =  throw new UnsupportedOperationException
	override def put(k:Key, v:Value): Value = {
		seqCache.put(k, Some(v)) match {
			case None => null.asInstanceOf[Value]
			case Some(v) => v
			case null => wrapped.get(k)
		}
	}
	def putAll(x$1: java.util.Map[_ <: Key, _ <: Value]): Unit =  throw new UnsupportedOperationException
	override def remove(k: Any): Value = {
		// This is an abomination but type erasure means it shouldn't matter
		seqCache.put(k.asInstanceOf[Key], None) match {
			case None => null.asInstanceOf[Value]
			case Some(v) => v
			case null => wrapped.get(k)
		}
	}
	def size(): Int =  throw new UnsupportedOperationException
	def values(): java.util.Collection[Value] = throw new UnsupportedOperationException
}

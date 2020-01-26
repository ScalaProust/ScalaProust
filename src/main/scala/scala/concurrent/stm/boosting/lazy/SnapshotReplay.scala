package scala.concurrent.stm.boosting.`lazy`

import scala.collection.mutable.Queue

class SnapshotReplay[T](override val wrapped:T, snapshot:T => T) extends ReplayLog[T]  {
	private val localCopy = snapshot(wrapped)
	
	override val log = Queue[T => Any]()
	override def apply[Z](f: T => Z): Z = f(localCopy)
	override def update[Z](f: T => Z):Z = {
		log.enqueue(f)
		f(localCopy)
	}

}
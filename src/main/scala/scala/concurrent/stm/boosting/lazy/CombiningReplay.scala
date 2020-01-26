package scala.concurrent.stm.boosting.`lazy`

trait MemoizerFactory[T,M <: T] {
	def apply(wrapped:T):M
}

trait CombiningReplayIteratorFromLocalCopy[T,M <: T] {
	def apply(localCopy:M):Iterator[T => Any]
}


class CombiningReplay[T,M <: T](override val wrapped:T)
						(	implicit val makeMemoizer:MemoizerFactory[T,M],
							implicit val makeLog:CombiningReplayIteratorFromLocalCopy[T,M])
						extends ReplayLog[T]  {
	
	private val localCopy = makeMemoizer(wrapped)
	
	override def log:Iterator[T => Any] = makeLog(localCopy)
	
	override def apply[Z](f: T => Z): Z = f(localCopy)
	override def update[Z](f: T => Z): Z = f(localCopy)

}
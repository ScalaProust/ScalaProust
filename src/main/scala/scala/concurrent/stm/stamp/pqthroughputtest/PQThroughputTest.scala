package scala.concurrent.stm.stamp.pqthroughputtest

import scala.annotation._
import scala.concurrent.stm.atomic
import scala.concurrent.stm.stamp.PriorityQueueAdapterTrait

object PQThroughputTest extends scala.concurrent.stm.stamp.Benchmark[(Int, Int, Int, Int, Float)]{
	/** As seen from object MapThroughputTest, the missing signatures are as follows. 
	 * For convenience, these are usable as stub implementations. 
	 */ 
	WARMUPS = 100
	RUNS = 50
	
	override val appName: String = "java PQThroughputTest"
	
	def getDefaultParams:ParamsType = (1, 1, 1024, 1024, 0.5f) 
	override def processParamAt(argv:Array[String]) = processParamAt(argv, 0, 0, getDefaultParams)
	
	override def displayUsage:Nothing = {
        Console.println(s"Usage: $appName -I path.to.ImplFactoryInstance$$ [options]")
        Console.println(s"\nOptions:                                             (defaults)")
        Console.println(s"    t <INT>     Number of [t]hreads                    (${getDefaultParams._1})")
        Console.println(s"    o <INT>     [o]perations/txn                       (${getDefaultParams._2})")
        Console.println(s"    i <INT>     [i]terations (across all threads/txns) (${getDefaultParams._3})")
        Console.println(s"    r <INT>     key[r]ange                             (${getDefaultParams._4})")
        Console.println(s"    u <FLOAT>   % of [u]pdate operations               (${getDefaultParams._5})")
        Console.println(s"    I <STR>     Classpath of [I]mplFactory            ")
		Console.println(s"    w <UINT>    Number of warmups                     ($WARMUPS)")
		Console.println(s"    runs <UINT> Number of runs                        ($RUNS)")
        super.displayUsage
	}
	
	@tailrec def processParamAt(argv:Array[String], i:Int, opterr:Int, params:ParamsType):(Int,ParamsType) = {
		val defaultInc = (2,0)
		if(i < argv.length){
			val nextArg = argv(i+1)
			val ((iplus:Int, errplus:Int), newParams:ParamsType) = argv(i) match {
				case "-runs" => RUNS=Integer.parseInt(nextArg); (defaultInc, params)
				case "-w" => WARMUPS=Integer.parseInt(nextArg); (defaultInc, params)
				case "-verbose" => VERBOSE = java.lang.Boolean.parseBoolean(nextArg); (defaultInc, params)
				case "-t" => (defaultInc, params.copy(_1 = Integer.parseInt(nextArg)))
				case "-o" => (defaultInc, params.copy(_2 = Integer.parseInt(nextArg)))
				case "-i" => (defaultInc, params.copy(_3 = Integer.parseInt(nextArg)))
				case "-r" => (defaultInc, params.copy(_4 = Integer.parseInt(nextArg)))
				case "-u" => (defaultInc, params.copy(_5 = java.lang.Float.parseFloat(nextArg)))
				case "-I" => parseAllocator(nextArg, params)
				case _ => delegateToAllocator(argv, i, params)
			}
			processParamAt(argv, i + iplus,opterr + errplus,newParams)
		} else {
			(opterr, params)
		}
		
	}
	
	private def txnLoop(pq:PriorityQueueAdapterTrait[Int], opCount:Int, opsPerTxn:Int, keyRange:Int, updatePct:Float):Unit = {
		val random = java.util.concurrent.ThreadLocalRandom.current
		Iterator.continually((random.nextInt(0, keyRange), random.nextFloat)).take(opCount).grouped(opsPerTxn).foreach{
			group =>
				atomic { implicit txn =>
					group.foreach{
						case (key, op) =>
							if(op < updatePct){
								if((op / updatePct) < 0.5){
									pq.removeMin
								} else {
									pq.insert(key)
								}
							} else {
								pq.min
							}
					}
				}
		}
	}
	
	protected def doBenchmark(params: ParamsType): Long = {
		val (threadCount, opsPerTxn, iterations, keyRange, updatePct) = params
		val barrier = new java.util.concurrent.CyclicBarrier(threadCount)
		val pq = ALLOCATOR.allocPQueue[Int]
		val perThread = iterations / threadCount
		
		class BenchClass extends Thread {
			override def run:Unit = {
				barrier.await
				txnLoop(pq, perThread, opsPerTxn, keyRange, updatePct)
				barrier.await
			}
		}
		if(VERBOSE) Console.println(s"Performing $iterations operations ($opsPerTxn / txn) over $threadCount threads ($perThread per) on $keyRange keys with $updatePct updates")
		val threads = (0 until (threadCount - 1)).map(i => new BenchClass)
		threads.foreach(_.start)
		val start = System.currentTimeMillis
		barrier.await
		txnLoop(pq, perThread, opsPerTxn, keyRange, updatePct)
		barrier.await
		val end = System.currentTimeMillis
		
		val delta = (end - start)
		if(VERBOSE) Console.println(s"Done in $delta ms.")
		delta
	}
}
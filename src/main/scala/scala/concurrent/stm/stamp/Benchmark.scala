package scala.concurrent.stm.stamp

import scala.util.{Try, Success}

trait Benchmark[Params <: Product] {
	type ParamsType = Params
	var WARMUPS:Int = 8000
	var RUNS:Int = 100
	var VERBOSE:Boolean = false
	var ALLOCATOR:ImplFactory = null
	
	def appName:String
	
	def displayUsage:Nothing = {
		Console.println(s"\nAdditional configuration for ImplFactory (${ALLOCATOR}):")
		Option(ALLOCATOR).foreach{_.displayUsage}
		scala.sys.exit(1)
	}
	
	def getDefaultParams:ParamsType
	protected def parseAllocator(nextArg:String, params:ParamsType):((Int, Int),ParamsType) = {
		((2, try {
					val implClass = Class.forName(nextArg)
					val implField = implClass.getDeclaredField("MODULE$")
					ALLOCATOR = implField.get(null).asInstanceOf[ImplFactory]
					0
				} catch {
					case e: Throwable => e.printStackTrace; 1
				}), params)
	}
	protected def delegateToAllocator(argv:Array[String], i:Int, params:ParamsType):((Int, Int),ParamsType) = {
		(Option(ALLOCATOR).map{alloc => alloc.parseArgs(argv, i)}.getOrElse((1, 1)), params)
	}
	protected def processParamAt(argv:Array[String]):(Int, ParamsType)
	protected def parseArgs(argv:Array[String]):ParamsType = {
		Try(processParamAt(argv)) match {
			case Success((opterr, params)) if (opterr == 0) && (ALLOCATOR != null) => params
			case _ => displayUsage
		}
	}
	
	protected def doBenchmark(params:ParamsType):Long
	
	def main(argv:Array[String]):Unit = {
		val params = parseArgs(argv)
		
		(0 until WARMUPS).foreach {
			i =>
				doBenchmark(params)
				if(VERBOSE) Console.println(i)
				System.gc
		}
		
		val timings = (0 until RUNS).map{
			i =>
				val ranFor:Double = doBenchmark(params)
				if(VERBOSE) Console.println(i)
				System.gc()
				ranFor
		}
		val mean = timings.sum / RUNS
		val variance = timings.map{ time => 
			val delta:Double = (time - mean)
			delta * delta
		}.sum / RUNS
	
		Console.println(s"COMPLETED $RUNS runs following $WARMUPS warmup runs.  Average runtime: $mean ms. Standard deviation: ${Math.sqrt(variance)} ms.")
		
	}
}
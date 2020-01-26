#!/usr/bin/env python

import json, subprocess, os.path
import os

def readConfig(argv, stdin):
	settings = json.load(stdin)
	threadSeq = settings["threadSeq"]
	sbtArgs = settings["sbtArgs"]
	benchConfs = settings["benchConfs"]
	implConfs = settings["implConfs"]
	outDir = settings.get("outDir",None)
	if outDir == None:
		if len(argv) > 1:
			outDir = argv[1]
		else:
			raise RuntimeError("outDir must be specified either in the input JSON or on the command line")
	return (settings, threadSeq, sbtArgs, benchConfs, implConfs, outDir)

REPORT_SCRIPT = os.path.expanduser("~/scala-tko-dev/bench/tkoreport.pl")


def flattenArgsDict(args):
	out = []
	for item in sorted(args.iteritems()):
		out += [str(i) for i in item]
	return out

def benchone(commandLine, outDir, benchmark, globalArgs, threads, package, runArgs):
	clString = subprocess.list2cmdline(commandLine)
	print clString
	outFile = os.path.join(outDir, "%s-%s-thr-%d-%s-%s.txt" % (benchmark, "".join(globalArgs), threads, package, "".join(runArgs)))
	with open(outFile,'w') as outHandle:
		outHandle.write("CONFIG:benchmark=%s,package=%s:%s,factor=threads,threads=%d,\n" % (benchmark, package, "".join(runArgs), threads))
		outHandle.flush()
		subprocess.call(commandLine,stdout=outHandle)
		#p = subprocess.Popen([REPORT_SCRIPT],stdin=subprocess.PIPE)
		#p.communicate(input=WHOLE_CONFIG)
		#p.wait()

def dumpToFile(obj, fname):
	with open(fname,'w') as fhandle:
		json.dump(obj, fhandle)


def benchloop(argv, stdin, dummy=False):
	(settings, threadSeq, sbtArgs, benchConfs, implConfs, outDir) = readConfig(argv, stdin)
	WHOLE_CONFIG = json.dumps(settings)
	if dummy:
		commands = []
		params = []
	for (benchmark, benchConf) in sorted(benchConfs.items()):
		benchPackage = benchmark.lower()
		for globalConf in benchConf:
			globalArgs = flattenArgsDict(globalConf)
			for threads in threadSeq:
				for (package, runs) in sorted(implConfs.items()):
					for run in runs:
						runArgs = flattenArgsDict(run)
						threadArg = "-c"
						if benchmark in ("Labyrinth","MapThroughputTest", "PQThroughputTest"):
							threadArg = "-t"
						commandLine = ["sbt"] + sbtArgs + [" ".join(["runMain","scala.concurrent.stm.stamp.%s.%s" % (benchPackage, benchmark), "-I","scala.concurrent.stm.%s.StampImpl$" % package] + runArgs + globalArgs + [threadArg,str(threads)])]
						if dummy:
							commands.append(commandLine)
							params.append([benchmark, globalArgs, threads, package, runArgs])
						else:
							benchone(commandLine, outDir, benchmark, globalArgs, threads, package, runArgs)
	if dummy:
		print "%d (?= %d)" % (len(commands), len(params))
		dumpToFile(commands, os.path.join(outDir, "commandSeq.json"))
		dumpToFile(params,os.path.join(outDir,"paramSeq.json"))


if __name__ == '__main__':
	import sys
	benchloop(sys.argv, sys.stdin, ("DUMMY" in os.environ) and int(os.environ['DUMMY']))

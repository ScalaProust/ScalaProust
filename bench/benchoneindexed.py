#!/usr/bin/env python

import json, subprocess, os.path
import os

from benchloop import benchone

def loadFromFile(fname):
	with open(fname,'r') as fhandle:
		return json.load(fhandle)


def benchoneindexed(outDir,i):
	commands = loadFromFile(os.path.join(outDir, "commandSeq.json"))
	params   = loadFromFile(os.path.join(outDir,"paramSeq.json"))
	
	i=int(i)
	
	commandLine = commands[i]
	(benchmark, globalArgs, threads, package, runArgs) = params[i]
	print "%d:\t" % i,
	benchone(commandLine, outDir, benchmark, globalArgs, threads, package, runArgs)

if __name__ == '__main__':
	import sys
	benchoneindexed(*sys.argv[1:])

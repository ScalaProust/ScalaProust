#!/usr/bin/env python

import json, subprocess, os, os.path
from benchloop import flattenArgsDict, readConfig, REPORT_SCRIPT




def graphloop(argv, stdin, overhead = False):
	(threadSeq, sbtArgs, benchConfs, implConfs, outDir) = readConfig(argv, stdin)[1:]
	
	candidateFiles = os.listdir(outDir)
	
	for (benchmark, benchConf) in sorted(benchConfs.items()):
		benchPackage = benchmark.lower()
		for globalConf in benchConf:
			globalArgs = flattenArgsDict(globalConf)
			
			prefix = "%s-%s-thr-" % (benchmark, "".join(globalArgs))
			prefLen = len(prefix)
			targets = [candidate for candidate in candidateFiles if candidate[:prefLen] == prefix]
			
			benchDir = os.path.join(outDir, prefix[:-4])
			
			if not os.path.isdir(benchDir): os.mkdir(benchDir)
			for target in targets:
				os.rename(os.path.join(outDir, target), os.path.join(benchDir, target))
			
			REPORT_SCRIPT = "bench/overheadreport.pl" if overhead else "bench/tkoreport.pl"
			repProc = subprocess.Popen([REPORT_SCRIPT],stdin=subprocess.PIPE)
			repProc.communicate(json.dumps({"threadSeq":threadSeq,"benchConfs":{benchmark:[globalConf]},"outDir":benchDir,"implConfs":implConfs}))
			if repProc.returncode:
				import sys
				sys.stderr.write("%s died with status %d\n" % (REPORT_SCRIPT, repProc.returncode))
				sys.exit(repProc.returncode)

if __name__ == '__main__':
	import sys
	graphloop(sys.argv, sys.stdin)
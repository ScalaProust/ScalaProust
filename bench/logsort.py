#!/usr/bin/env python

import os, os.path

files = os.listdir(os.getcwd())
dirs = [file for file in files if os.path.isdir(file)]
for dir in dirs:
	for file in files:
		if os.path.isfile(file) and dir in file:
			os.rename(file,os.path.join(dir,file))

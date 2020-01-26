#!/bin/bash

pushd ..
PATH=$PATH:/home/tddicker/bin bench/benchoneindexed.py bench/batches/tmap/logs/ $(($SGE_TASK_ID-1))
popd

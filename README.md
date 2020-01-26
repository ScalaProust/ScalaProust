#			 Scala TKO development 


This is Scala TKO, a software transactional memory library.

## SUMMARY

       $ git submodule init  # to get scala-stm
       $ git submodule update
       $ apt-get install sbt
       $ sbt compile
       $ sbt run

If using Ubuntu Desktop 16.04, first downgrade to Java 8:

       # Java 1.8
       $ sudo apt-get install openjdk-8-jre openjdk-8-jdk
       $ sudo update-java-alternatives -l
       $ sudo update-java-alternatives -s <whatever is 1.8>



## PREREQUISITES

For development, you need to have [SBT](http://www.scala-sbt.org/) installed. 

Also, you must install:

       $ sudo apt-get install libjson-perl


## IDE DEVELOPMENT

If you prefer to work in an IDE, Eclipse project files can be generate by executing `sbt eclipse`. You may have to manually exclude `scala.concurrent.stm.stamp.{kmeans,labyrinth}` from your project files for now.


## COMPILATION

After installing SBT, simply go to the root directory of this project
and run `sbt`, this will start sbt in interactive mode. You just have
to run `compile` in interactive mode. Alternatively, you can ask sbt
to automatically recompile whenever you save a source file by run `~
compile` in interactive mode.

If you want to change the dependencies or Scala version, modify the
content of build.sbt and then restart sbt.


## EXECUTION

   $ sbt
   ...
   
   > run -I scala.concurrent.stm.boosting.StampImpl$ -c 7 -verbose true

If you want to run the some benchamrks on GridEngine:

   $ cat bench/batches/tmap/all.json | DUMMY=1 bench/benchloop.py
   165 (?= 165)
   $ cd tmp
   # Fill in the number of jobs
   $ qsub -t 1-165 -l short -pe smp 32 -q '*@@mblade12' -cwd ../bench/gridenginetask.sh

## NOTICE

An older version of this code which may result in **deadlocks** can be found at the original [repository](https://bitbucket.org/mherlihy/tko).

#!/usr/bin/perl

use POSIX;
my @batch_names = @ARGV;

#################################
# Run the benchmarks

my $root = "~/scala-tko-dev";
    
chdir $root;

foreach my $batch (@batch_names) {
    # my $cfg = "";
    # open TMP, "$root/bench/${batch}.json" or die $!;
    # while(<TMP>) { $cfg .= $_; }
    # close TMP;
    print "-- batch: $batch\n";

    my $STAMP = strftime '%Y%m%d-%H%M%S', gmtime();
    my $RESULTSDIR = "/var/www/html/scalatko/batch/$batch-$STAMP";
    mkdir $RESULTSDIR;
    chmod 0777, $RESULTSDIR;

    my $batchFile = "$root/bench/batches/${batch}.json";
    qx{perl -p -i -e 's("outDir":"[^"]+")("outDir":"$RESULTSDIR")' $batchFile};
    qx{cat $batchFile > $RESULTSDIR/config.txt};
    qx{cat $batchFile | $root/bench/benchloop.py};
}

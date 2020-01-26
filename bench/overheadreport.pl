#!/usr/bin/perl

# var/www/scalatko/results-20161027-180943/
# http://gnuplot.sourceforge.net/docs_5.0/gnuplot.pdf
# Page 134
##################################################################################    

use JSON::PP;
use Data::Dumper;
print "tkoreport.pl - \n";
my $json = "";
while(<STDIN>) { $json .= $_; }
print "json: $json\n";
my $config = decode_json($json);
my @BENCHES = keys %{$config->{benchConfs}};
my $RESULTSDIR = $config->{outDir};
print "dir: $RESULTSDIR\n";
my @THREADS = @{$config->{threadSeq}};

##################################################################################    
# Load results

my $results; my %fqtrends;
opendir(my $dh, $RESULTSDIR) || die "Can't open $RESULTSDIR: $!";
while (readdir $dh) {
    my $fn = "$RESULTSDIR/$_";
    #print "consider $fn";
    if ($fn =~ /\.txt/) {
	open TMP, $fn or die $!;
	my ($b,$p,$f,$v);
	while(<TMP>) {
	    if (/CONFIG:benchmark=(.*),package=(.*),factor=threads,threads=(\d+),/) {
		($b,$p,$f,$v) = ($1,$2,"threads",$3);
		$fqtrends{$p} = 1;
	    } elsif (/COMPLETED.* Average runtime: (.*) ms. Standard deviation: (.*) ms./) {
		$results->{$b}->{$p}->{$f}->{$v} = { avg => $1, dev => $2 };
	    }
	}
	close TMP;
    }
}    
closedir $dh;

# Decide the full trends
delete $fqtrends{"traditional:"};
delete $fqtrends{"predication:"};
delete $fqtrends{"dummy.juc:"};
my @TRENDS = ((keys %fqtrends));
use Data::Dumper;
print Dumper(\@TRENDS);
#print Dumper($results);

my $trTitle = {
	# Generic Titles
    'traditional:' => 'Traditional',
    'predication:' => 'Predication',
	'dummy:' => 'Dummy',
	# PQueue Titles
	'boosting:-LMOptimistic-NSfalse-PQVBraunHeap' => 'Proust[Lazy/Opt]-BraunHeap',
	'boosting:-LMOptimistic-NStrue-PQVBraunHeap' => 'Proust[Lazy/Opt]-BraunHeap (NS)',
	
	# Map Titles
    'boosting:-LMPessimistic-MVTrieMap-NSfalse'    => 'Proust[Eager/Pess]-TrieMap',
    'boosting:-LMPessimistic-MVTrieMap-NStrue'    => 'Proust[Eager/Pess]-TrieMap (NS)',
    'boosting:-LMOptimistic-MVLazyTrieMap-NSfalse' => 'Proust[Lazy/Opt]-TrieMap',
    'boosting:-LMOptimistic-MVLazyTrieMap-NStrue' => 'Proust[Lazy/Opt]-TrieMap (NS)',
    'boosting:-LMOptimistic-MVTrieMap-NSfalse'     => 'Proust[Eager/Opt]-TrieMap',
    'boosting:-LMOptimistic-MVTrieMap-NStrue'     => 'Proust[Eager/Opt]-TrieMap (NS)',
    'boosting:-LMOptimistic-MVLazyHashMap-NSfalse'     => 'Proust[Lazy/Opt]-HashMap',
    'boosting:-LMOptimistic-MVLazyHashMap-NStrue'     => 'Proust[Lazy/Opt]-HashMap (NS)',
    'boosting:-LMOptimistic-MVCombiningLazyHashMap-NSfalse'     => 'Proust[Lazy/Opt]-CHashMap',
    'boosting:-LMOptimistic-MVCombiningLazyHashMap-NStrue'     => 'Proust[Lazy/Opt]-CHashMap (NS)'
};

my $trLineType = {
    # Generic LTs
    'traditional:' => 1,
    'predication:' => 2,
    # PQueue LTs
	'boosting:-LMOptimistic-NSfalse-PQVBraunHeap' => 4,
	'boosting:-LMOptimistic-NStrue-PQVBraunHeap' => 7,
	
	# Map LTs
	'boosting:-LMPessimistic-MVTrieMap-NSfalse'    => 3,
    'boosting:-LMOptimistic-MVLazyTrieMap-NSfalse' => 4,
    'boosting:-LMOptimistic-MVTrieMap-NSfalse'     => 5,
	'boosting:-LMPessimistic-MVTrieMap-NStrue'    => 6,
    'boosting:-LMOptimistic-MVLazyTrieMap-NStrue' => 7,
    'boosting:-LMOptimistic-MVTrieMap-NStrue'     => 8,
    'boosting:-LMOptimistic-MVLazyHashMap-NSfalse' => 4,
    'boosting:-LMOptimistic-MVCombiningLazyHashMap-NSfalse'     => 5,
    'boosting:-LMOptimistic-MVLazyHashMap-NStrue' => 7,
    'boosting:-LMOptimistic-MVCombiningLazyHashMap-NStrue'     => 8
};
    
my $expMaxY = {
	'MapThroughputTest' => 250,
	'PQThroughputTest' => 800
};
	
##################################################################################

my %factornames = (
    "threads", "Number of Threads" );

sub gnuplot_overhead {
    my ($SC,$factor,$dummy_idx,@trns) = @_;
	my $dummy_tr = $trns[$dummy_idx];
    open TMP, ">$RESULTSDIR/plot-commands-$SC-$factor-overhead" or die $!;
    print TMP <<EOT;
set terminal pdfcairo size 4.00in,3.00in enhanced font 'Verdana,10'
set termoption dash
set title "$SC"
set xlabel "$factornames{$factor}"
set ylabel "Mean Time (ms)"
set yrange [0:8] #$expMaxY{$SC}]
set xrange [0:32]
set output "$RESULTSDIR/$SC-$factor-overhead.pdf"
set linetype 1 lw 1 lc rgb "blue" pointtype 4
set linetype 2 lw 1 lc rgb "forest-green" pointtype 5
set linetype 3 lw 1 lc rgb "plum" pointtype 6
set linetype 4 lw 1 lc rgb "orange" pointtype 7
set linetype 5 lw 1 lc rgb "red" pointtype 8
set linetype 6 lw 1 lc rgb "dark-plum" pointtype 9
set linetype 7 lw 1 lc rgb "dark-orange" pointtype 10
set linetype 8 lw 1 lc rgb "dark-red" pointtype 11
set nokey
set notitle
set noxlabel
set noylabel
set ytics in
#set ytics format " "
EOT
  print TMP "plot ";
  my $idx = 0;
  foreach my $tr (@trns) {
	
	if($idx != $dummy_idx) {
	  my $lt = $trLineType->{$tr->{name}};
	  print "Emitting $tr->{name} ($idx is not $dummy_idx)\n";
	  my $overhead_exp = "(\$$tr->{avg_col} / \$$dummy_tr->{avg_col})";
	  my $relative_err = "(\$$tr->{dev_col} / \$$tr->{avg_col})";
	  my $relative_dummy_err = "(\$$dummy_tr->{dev_col} / \$$dummy_tr->{avg_col})";
	  my $relative_overhead_err = "((($relative_err ** 2) + ($relative_dummy_err ** 2)) ** 0.5)";
	  my $overhead_err = "($relative_overhead_err * abs($overhead_exp))";
	  print "\t$overhead_err error for $overhead_exp\n";
      print TMP qq{   "$RESULTSDIR/$SC-$factor-overhead.dat" using 1:$overhead_exp:$overhead_err title '$trTitle->{$tr->{name}}' with yerrorbars lt $lt, \\\n};
      print TMP qq{   "$RESULTSDIR/$SC-$factor-overhead.dat" using 1:$overhead_exp notitle w lines lt $lt, \\\n};	
	}
	$idx++;
  }
  close TMP;
    
  qx{gnuplot $RESULTSDIR/plot-commands-$SC-$factor-overhead};

}

##################################################################################

print "benches @BENCHES\n";

foreach my $StampBench (@BENCHES) { # MapThroughputTest Vacation
	print "- in bench: $StampBench\n";
  foreach my $factor (qw/threads/) {
	print "-- in factor $factor\n";
	print "-- writing to >$RESULTSDIR/$StampBench-$factor-overhead.dat\n";
    open OUT, ">$RESULTSDIR/$StampBench-$factor-overhead.dat" or die $!;
    print OUT "# $StampBench - $factor\n";
    print OUT "# $factor\t".join("\t",map($trTitle->{$_},@TRENDS))."\n";
    my @trs;
	my $dummy_idx = -1;
    for(my $i=0;$i<=$#TRENDS; $i++) {
	my $col = $i+1;
	my $tr = { avg_col => 2*$col,     # 2, 4, 6, 8
		   dev_col => (2*$col)+1, #   3  5  7 9
		   speedup_col => 1+$col, # 2,3,4,5,6
		   name => $TRENDS[$i]};
	push @trs, $tr;
	if($TRENDS[$i] eq "dummy:"){
		print "Found dummy: at $i ($TRENDS[$i])\n";
		$dummy_idx = $i;
	} else {
		print "dummy: is not at $i ($TRENDS[$i])\n";
	}
    }
	print "Post search, dummy was at $dummy_idx\n";
    #print Dumper(\@trs);
	
    my $trad_avg_base = 0;
    foreach my $v (@THREADS) {
	#my $trad_avg = $results->{$StampBench}->{"traditional:"}->{$factor}->{$v}->{avg};
	#my $trad_dev = $results->{$StampBench}->{"traditional:"}->{$factor}->{$v}->{dev};
	my $out_str = "";
	#my $speed_str = "";
	#$trad_avg_base = $trad_avg if ($v == 2);
	foreach my $trend (@TRENDS) {
	    my $t_avg = $results->{$StampBench}->{$trend}->{$factor}->{$v}->{avg};
	    my $t_dev = $results->{$StampBench}->{$trend}->{$factor}->{$v}->{dev};
	    $out_str .= "\t$t_avg\t$t_dev";
	    $t_avg = 10000000 if $t_avg eq '';
	    #$speed_str .= "\t".($trad_avg_base / $t_avg);
	}
	print OUT "$v".$out_str."\n";
	}
    close OUT;
    
    gnuplot_overhead($StampBench,$factor,$dummy_idx,@trs);
  }
}

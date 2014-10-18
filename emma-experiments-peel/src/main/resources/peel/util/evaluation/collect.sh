#!/bin/bash

. ./utils.sh

DIR_RESULTS=../../result/final
DOP="40 80 120 160 200"
EXPERIMENT="wc-str_pact wc_jst-str_pact wc-hdp_mapr ts-hdp_mapr ts-str_pact ts_otf-hdp_mapr ts_otf-str_pact q3-hdp_hive q3-str_pact cc-hdp_grph cc-str_iter te_pokec-ozn_pact te_pokec-hdp_mapr"

for exp in $EXPERIMENT; do
	OUTPUT_FILE="csv/$exp.csv"

	##
	# Main loop
	##

	> $OUTPUT_FILE
	for dop in $DOP; do
		if [[ $exp =~ ^cc-hdp_grph|cc-str_iter$ ]] ; then
			sf=37
                elif [[ $exp =~ ^te_pokec-hdp_mapr|te_pokec-ozn_pact$ ]] ; then
			sf=37
		else
			sf=$[dop/4]
		fi
		dir=`printf "%s-sf%04d-dop%04d" ${exp} ${sf} ${dop}`
		runtime=`getRuntime $dir`
		if [ $runtime ]; then # check that we have a result
			echo "$dop,$runtime" >> $OUTPUT_FILE
		fi
	done # dop
done # experiment





#!/bin/bash

# $1 is filename
function getRuntime {
	ARR=()
	for run in 01 02 03; do
		FULL_PATH=$DIR_RESULTS/$1-run$run/run.log
		if ! [ -a $FULL_PATH ]; then # check if file exists
			exit
		fi
		ARR+=(`cat $FULL_PATH | tr -s [:space:] | cut --delimiter=' ' -f 2`)
	done
	#find maximum and minimum
	max=0
	min=100000000000
	for n in "${ARR[@]}" ; do
    	((n > max)) && max=$n
    	((n < min)) && min=$n
	done
	median=$((${ARR[0]}+${ARR[1]}+${ARR[2]}-$max-$min)) 
	echo $median #return average
}


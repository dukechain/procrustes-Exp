#!/bin/bash

host=$1
dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
src="$( dirname $( dirname $dir ) )"

if ! [[ -f "$dir/$host.config" ]]; then
   echo "Could not find configuration file '$dir/$host.config'."
   echo "You need to specify a valid host name as first argument. Canceling..."
   exit 1
fi

# include config
. $dir/$host.config

# execute rsync
echo ""
echo "Fetching files from remote host."
rsync -L -a -v -r -e "ssh -l ${host_user}" --exclude-from="${dir}/rsync.excludes" ${host_name}:${host_dest}/. $src/

#!/bin/sh

# Location of the ospl testlib:
export TESTLIB_HOME=
# Location of the TAO:
export TAO_ROOT=
export ACE_ROOT=$TAO_ROOT

# Set publisher hostname, executable and port:
export PUB_PORT=8000
export PUB_WAITER=TRUE

# Set another publisher hostname, executable and port:
export ANOTHER_PUB_HOST=
export ANOTHER_PUB_PORT=9000

# Path to the libraries:
export LD_LIBRARY_PATH=$TAO_ROOT/lib:$TESTLIB_HOME:$LD_LIBRARY_PATH

ospl start
sleep 1
./run_subscriber.sh 1 &
sleep 1
./run_publisher.sh 1 2
ospl stop

unset PUB_WAITER

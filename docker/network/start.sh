#!/bin/sh

overlay=$1
dissemination=$2
servernode=$(hostname)
exppath=run$3

mkdir logs/$exppath

java -DlogFilename=${exppath}/${overlay}_${dissemination}_${servernode} -cp ./net.jar Main -overlay $overlay -dissemination $dissemination -babelConfFile ./network_config.properties $4 2> logs/${exppath}/${overlay}_${dissemination}_${servernode}.err

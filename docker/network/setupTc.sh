#!/bin/sh

idx=$1
bandwith=$2
latencyMap="config/latencyMap.txt"
ipsMap="config/ips.txt"

if [ -z $bandwith ]; then
  bandwith=1000
fi

ips=""
while read -r ip
do
  ips="${ips} ${ip}"
done < "$ipsMap"


function setuptc {

  Inbandwith=$((bandwith*2))

  cmd="modprobe ifb numifbs=1"
  echo "$cmd"
  eval $cmd

  cmd="ip link add ifb0 type ifb"
  echo "$cmd"
  eval $cmd

  cmd="ip link set dev ifb0 up"
  echo "$cmd"
  eval $cmd


  cmd="tc qdisc add dev eth0 handle ffff: ingress"
  echo "$cmd"
  eval $cmd

  cmd="tc filter add dev eth0 parent ffff: protocol ip u32 match u32 0 0 action mirred egress redirect dev ifb0"
  echo "$cmd"
  eval $cmd


  cmd="tc qdisc add dev ifb0 root handle 1: htb default 1"
  echo "$cmd"
  eval $cmd

  cmd="tc class add dev ifb0 parent 1: classid 1:1 htb rate ${Inbandwith}mbit"
  echo "$cmd"
  eval $cmd

  cmd="tc qdisc add dev eth0 root handle 1: htb"
  echo "$cmd"
  eval $cmd
  j=1

  cmd="tc class add dev eth0 parent 1: classid 1:1 htb rate ${bandwith}mbit"
  echo "$cmd"
  eval $cmd



  for n in $1
  do
    cmd="tc class add dev eth0 parent 1: classid 1:${j}1 htb rate ${bandwith}mbit"
    echo "$cmd"
    eval $cmd
    targetIp=$(echo ${ips} | cut -d' ' -f${j})
    cmd="tc qdisc add dev eth0 parent 1:${j}1 netem delay ${n}ms"
    echo "$cmd"
    eval $cmd
    cmd="tc filter add dev eth0 protocol ip parent 1:0 prio 1 u32 match ip dst $targetIp flowid 1:${j}1"
    echo "$cmd"
    eval $cmd
    j=$((j+1))
  done
}

i=0
echo "Setting up tc emulated network..."
while read -r line
do
  if [ $idx -eq $i ]; then
    setuptc "$line"
    break
  fi
  i=$((i+1))
done < "$latencyMap"

echo "Done."

/bin/sh

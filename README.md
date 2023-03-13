# Edge Overlays - Overlay Protocols for Edge Computing Management

Here you will find the java implementations of popular overalay networks for p2p, and a syntethic edge computing configuration using docker containers.

Available overlay protocols:
- HyParView
- X-Bot
- T-Man
- Cyclon
- Bias Layered Tree

Available dissemination protocols:
- Flood Gossip
- PlumTree

## Setup & Execution
In the ``docker`` folder you will find the configuration to build the docker image, and configurations to execute an emulated edge network with 100 nodes.
The ``net.jar`` is the compiled java code.

To run a container use the following:
```
docker run --rm $logVol -d -t --cpus=$cpu --privileged -v /lib/modules:/lib/modules --cap-add=ALL --net $net --ip $ip --name $name -h $name $image $i $bandwidth
```

Where:
- ``$logVol`` should be docker mount (or volume) option that points to ``/code/logs/`` in the container. 
E.g., ``--mount type=bind,source=/home/user/logs,target=/code/logs``.
- ``$cpu`` is the number of cpus assigned to the container. This is effectively a cpu quota see [docker docs](https://docs.docker.com/config/containers/resource_constraints/#cpu).
- ``$net`` is the name of a user created docker network that is attachable. 
We advice the use of ``docker network create -d overlay --attachable --subnet 10.10.0.0/16 --gateway 10.10.0.1 $net`` command to create the  network named ``$net``, to be compatible with the provided configuration and able to spread the containers among different machines.
- ``$ip`` is an ip address provided in the configuration.
- ``$name`` is a hostname provided in the configuration.
- ``$image`` is the name of the created docker image.
- ``$i`` is the line number of the configuration file used.
- ``$bandwidth`` is the bandwidth value in mbit/s assigned to the container.

The container will execute a set of Linux ``tc`` commands (in script ``setupTc.sh``) to setup the emulated network and wait.

The configuration file ``config.txt`` has for each line:
``<level> <ip> <name>``

The level is used as value to determine how far the node is from the cloud, and consequently how much resources it has assigned.


To iterate over the configuration to launch all the containers, you can create a bash script similar to this:
```sh
maxcpu=$(nproc)
base=1000

i=0
echo "Lauching containers..."
while read -r level ip name
do
  case $layer in
  0)
    let cpu=$maxcpu/2
    let bandwidth=$base
    ;;
  1)
    let cpu=$maxcpu/3
    let bandwidth=$base/2
    ;;
  2)
    let cpu=$maxcpu/4
    let bandwidth=$base/4
    ;;
  3)
    let cpu=$maxcpu/5
    let bandwidth=$base/8
    ;;
  *)
    let cpu=$maxcpu/6
    let bandwidth=$base/20
    ;;
  esac


  logVol="--mount type=bind,source=/home/user/logs,target=/code/logs"
  cmd="docker run --rm $logVol -d -t --cpus=$cpu --privileged -v /lib/modules:/lib/modules --cap-add=ALL --net $net --ip $ip --name $name -h $name $image $i $bandwidth"

  echo "$cmd"
  eval "$cmd"
  echo "${i}. Container $name with ip $ip lauched"
  i=$((i+1))
done < "config.txt"
```

If you want to execute over more than one machine, you should change the line ``eval $cmd`` by an ``ssh`` comand where you round-robin the available machines for example.
E.g.:
```sh
function nextnode {
  local idx=$(($1 % $n_machines))
  local i=0
  for host in $machines; do
    if [ $i -eq $idx ]; then
      echo $host
      break;
    fi
    i=$(($i +1))
  done
}

 ...
  node=$(nextnode $i)
  ssh -n $node $cmd
 ...
```


### Execution
Once all the containers are up and running. You can execute the java application through ``docker exec`` commands.

For the bootstrap (or contact) node use:
```
docker exec -d $name ./start.sh $overlay $dissemination $runNumber '-babelConf layer=$layer $otherArgs'
```

For the remainder use:
```
docker exec -d $name ./start.sh $overlay $dissemination $runNumber '-babelConf layer=$level -babelConf contacts=${contact}:10000 $otherArgs'
```

Where:
- ``$name`` is the name of the container.
- ``$overlay`` is the name of the overlay you want to use.
- ``$dissemination`` is the name of the dissemination protocol you want to use.
- ``$runNumber`` is a number to distinguish different executions of the same set of protocols (used for logging).
- ``$level`` is the level assigned to the java application.
- ``$contact`` is the ip address of the contact node.
- ``$otherArgs`` is a list of additional arguments in the form ``"-babelConf <option1>=<value1> -babelConf <option2>=<value2> ..."``

### Thanks
This work was partially funded by project [NG-Storage](https://asc.di.fct.unl.pt/~jleitao/ngstorage.php) (PTDC/CCI-INF/32038/2017).


# A fun hack project....

## Setup:

### Description:

This project is using Docker swarm and docker-compose to spin up a virtual cluster to simulate a production like environment locally.
This docker-compose configuration will spin up 1 zookeeper, 1 kafka, 1 mesos master, 1 mesos slave (scaleable by use command `docker-compose scale mesos_slave=<number>`)

what is [apache mesos](http://mesos.apache.org/)

If you are interesting to read: Setting up [Docker swarm with overlay network](https://docs.docker.com/engine/userguide/networking/get-started-overlay/)

### Prerequest:

Install Docker: [Download Docker](https://docs.docker.com/mac/step_one/)

Make sure you can use docker and docker-compose

### Setting Docker vm to simulate physical nodes:

#### Setting up Docker swarm environment:

what is [Docker swarm](https://docs.docker.com/swarm/)

1. Setting up a keystore for docker swarm, we use consul here:

```
docker-machine create -d virtualbox mh-keystore
eval "$(docker-machine env mh-keystore)"
docker run -d     -p "8500:8500"     -h "consul"     progrium/consul -server -bootstrap
```

2. creating vms and join the swarm network (showing example to create 2 vms, you can adjust parameters with your needs)
```
docker-machine create -d virtualbox --virtualbox-memory 4096 --virtualbox-cpu-count 2 --swarm --swarm-master --swarm-discovery="consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-store=consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-advertise=eth1:2376" mhs-demo0
docker-machine create -d virtualbox --virtualbox-memory 4096 --virtualbox-cpu-count 2 --swarm --swarm-master --swarm-discovery="consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-store=consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-advertise=eth1:2376" mhs-demo1
```

3. Switch to use a swarm node (make sure to include the --swarm flag):

```
eval $(docker-machine env --swarm mhs-demo1)
```

4. Check how many vms you have:
```
docker-machine ls
```

#### Starting docker instance with docker-compose:

what is [docker-compose](https://docs.docker.com/compose/)

in the directory contains docker-compose.yml (which is the root dir of this project, also it will pull about 2gb image files)

```
docker-compose up
```

run docker compose in background:
```
docker-compose up -d
```
docker will pull all necessary images, run services, and connect them in the swarm network

sometimes the network is not stable, it is possible one or more nodes die on start (it is possible to configure HA to automatically recover from this situation, but I didn't do it) In that case, stop all service, remove them and start it again:

```
docker-compose stop
docker-compose rm
docker-compose up
```

if docker-compose rm does not remove them, use

```
docker ps -a
```
will show you all docker instances

then use following command to delete
```
docker rm -f <name>
```

## Usage:

### See which docker images is running and check the name:
```
docker ps
```

### Attach a new shell process inside a running Docker instance:
```
docker exec -it <name> /bin/bash
```

## Play with the Heartbeat app:

### Preparation:
1. build the jar:
```
sbt assembly
```

2. find the kafka, mesos_slave docker instance name:
```
docker ps
# looking for the name <node>/testcompose_mesos_slave_<number>, <node>/testcompose_kafka_master_<number>
```

You should see something like this:

|CONTAINER ID    |    IMAGE                 |                             COMMAND            |      CREATED      |       STATUS        |      PORTS             |                                           NAMES|
| ------------- |:-------------:| :-----:| :-------------: |:-------------:| :-----:|-----:|
| 97689eb58196    |     xkd0413/mesos_slave          |                       "mesos-slave --launch" |   2 hours ago  |        Up 2 hours     |      4040/tcp, 7001-7006/tcp, 7077/tcp, 8080-8081/tcp, 8888/tcp |   mhs-demo0/testcompose_mesos_slave_1| 
| b273339cd714   |      wurstmeister/kafka           |                       "/opt/kafka_2.11-0.9."  |  2 hours ago        |  Up 2  hours        |   192.168.99.101:9092->9092/tcp  |                               mhs-demo0/testcompose_kafka_master_1| 
| 5c9ad277e803   |      mesosphere/mesos-master:0.28.0-2.0.16.ubuntu1404  |  "mesos-master --regis" |   2 hours ago        |  Up 2  hours        |   192.168.99.102:5050->5050/tcp          |                       mhs-demo1/testcompose_mesos_master_1| 
| 3cc92b74b90e    |     netflixoss/exhibitor:1.5.2             |             "java -jar exhibitor-"  |  2 hours ago      |    Up 2  hours         |  2181/tcp, 2888/tcp, 3888/tcp, 8080/tcp    |                    mhs-demo1/testcompose_zk_1| 
| 

If you notice the port mapping on the mesos_master, you can also open that url to check mesos cluster's status

3. create/consume kafka topic from shell:
```
docker exec -it <node>/testcompose_kafka_master_<number> /opt/kafka_2.11-0.9.0.1/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic <kafka_topic>
docker exec -it <node>/testcompose_kafka_master_<number> /opt/kafka_2.11-0.9.0.1/bin/kafka-console-consumer.sh --zookeeper zk:2181 --topic <kafka_topic> --from-beginning

# note: zk:2181 is the zookeeper network address inside the docker swarm, swarm sets up a internal DNS with overlay network
```

Right now the data format is assuming in: 

`<device_id> <heartbeat_rate>`

delimiter is space

basically `docker exec -it <docker-instance-name> <command>`

### Build decision tree model:
```
docker exec -it <node>/testcompose_mesos_slave_<number> python /pysparkFile/train.py /pysparkFile/train.csv
# the code and training data is already placed inside the docker image by linking a shared folder
# it will generate/save model file under /decisiontree
```

### Streaming the input, process it and push to herokuConnect
```
docker exec -it <node>/testcompose_mesos_slave_<number> /usr/local/spark/bin/spark-submit --master mesos://mesos_master:5050 --class com.example.spark.DirectKafkaWordCount app/direct_kafka_word_count.jar kafka_master:9092 <kafka_topic>

# note: mesos_master:5050 and kafka_master:9092 is the actual network address
```

### change the heroku endpoint:
open the `DirectKafkaWordCount.scala` file, change the url inside the Http(....), rebuild the scala program

## Related docker images:

[zookeeper](https://hub.docker.com/r/netflixoss/exhibitor/)

[mesos master](https://hub.docker.com/r/mesosphere/mesos-master/)

build a [mesos_slave](https://github.com/kaidix/mesos_slave) image which includes all necessary spark libraries

[kafka](https://hub.docker.com/r/wurstmeister/kafka/)




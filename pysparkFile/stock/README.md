# Stock prediction hack project

## Setup:

### Prerequest:

Install Docker: [Download Docker](https://docs.docker.com/mac/step_one/)

Make sure you can use docker and docker-compose

### Setting Docker vm to simulate physical nodes:

#### Setting up Docker swarm environment:


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

#### Starting docker instance with docker-compose:


in the directory contains docker-compose.yml (which is the root dir of this project, also it will pull about 2gb image files)

```
docker-compose up
```


4. find the kafka, mesos_slave docker instance name:
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

5. create/consume kafka topic from shell:
```
docker exec -it <node>/testcompose_kafka_master_<number> /opt/kafka_2.11-0.9.0.1/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic <kafka_topic>
docker exec -it <node>/testcompose_kafka_master_<number> /opt/kafka_2.11-0.9.0.1/bin/kafka-console-consumer.sh --zookeeper zk:2181 --topic <kafka_topic> --from-beginning

# note: zk:2181 is the zookeeper network address inside the docker swarm, swarm sets up a internal DNS with overlay network
```

basically `docker exec -it <docker-instance-name> <command>`

Type message in the producer shell and hit enter will push line to kafka topic

Right now the data format is assuming in: 

`<stock_symbol> <close> <ema>`

delimiter is space

6. cd to the stock dir, run command
```
sbt assembly
```

7. open a shell attach to mesos_slave:

```
docker exec -it <node>/testcompose_mesos_slave_<number> /bin/bash
```

Then in the shell, run following command to take input from kafka:
```
cd /pysparkFile/stock
/usr/local/spark/bin/spark-submit --master mesos://mesos_master:5050 --class SimpleApp target/scala-2.10/simple-project_2.10-1.0.jar kafkaEvaluate kafka_master:9092 <kafka_topic>

# note: for some reason, local mode doesn't work if we pointing spark to kafka
```

or run locally by reading input.csv 

```
/usr/local/spark/bin/spark-submit --master local --class SimpleApp target/scala-2.10/simple-project_2.10-1.0.jar evaluate
```


### change the heroku endpoint:
open the `stock/SimpleApp.scala` file, change the url inside the Http(....), rebuild the scala program


docker-machine create -d virtualbox mh-keystore
eval "$(docker-machine env mh-keystore)"
docker run -d     -p "8500:8500"     -h "consul"     progrium/consul -server -bootstrap
docker-machine create -d virtualbox --virtualbox-memory 4096 --virtualbox-cpu-count 2 --swarm --swarm-master --swarm-discovery="consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-store=consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-advertise=eth1:2376" mhs-demo0
docker-machine create -d virtualbox --virtualbox-memory 4096 --virtualbox-cpu-count 2 --swarm --swarm-master --swarm-discovery="consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-store=consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-advertise=eth1:2376" mhs-demo1
eval $(docker-machine env mhs-demo0)
docker network create --driver overlay --subnet=10.0.9.0/24 my-net


https://docs.docker.com/engine/userguide/networking/get-started-overlay/

eval $(docker-machine env --swarm mhs-demo1)
docker-compose up -d
## if complains timeout...
## docker-compose stop
## docker-compose rm
## docker-compose up -d

docker exec -it mhs-demo0/sparkkafkadockerdemo2_mesos_slave_1 /usr/local/spark/bin/spark-shell --master mesos://mesos_master:5050

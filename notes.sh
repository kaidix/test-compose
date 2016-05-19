docker-machine create -d virtualbox mh-keystore
eval "$(docker-machine env mh-keystore)"
docker run -d     -p "8500:8500"     -h "consul"     progrium/consul -server -bootstrap
docker-machine create -d virtualbox --swarm --swarm-master --swarm-discovery="consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-store=consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-advertise=eth1:2376" mhs-demo0
docker-machine create -d virtualbox --swarm --swarm-master --swarm-discovery="consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-store=consul://$(docker-machine ip mh-keystore):8500" --engine-opt="cluster-advertise=eth1:2376" mhs-demo1
eval $(docker-machine env mhs-demo0)
docker network create --driver overlay --subnet=10.0.9.0/24 my-net

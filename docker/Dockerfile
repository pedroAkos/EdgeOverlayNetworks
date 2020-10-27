FROM openjdk:8-alpine
LABEL maintainer="Pedro Ákos Costa <pah.costa@campus.fct.unl.pt>"

RUN apk add --no-cache \
                bind-tools \
                iproute2 \
                nload



WORKDIR code
ADD network/start.sh ./start.sh
ADD network/net.jar ./net.jar
ADD network/network_config.properties ./network_config.properties
ADD network/log4j2.xml .

RUN mkdir config

#ADD config/inet100Latencies_x0.005.txt config/latencyMap.txt
ADD config/inet100Latencies_x0.04.txt config/latencyMap.txt
#ADD config/ips100.txt config/ips.txt
ADD network/setupTc.sh .
ADD config/ips100.txt config/ips.txt

EXPOSE 10000/tcp


ENTRYPOINT ["./setupTc.sh"]
#CMD ["/bin/sh"]
#ADD tools/* ./
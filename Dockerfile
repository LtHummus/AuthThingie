FROM openjdk:12-oracle
ADD target/universal/traefikcop-1.0-SNAPSHOT.tgz /svc
EXPOSE 9000 9443 5005
CMD /svc/traefikcop-1.0-SNAPSHOT/bin/traefikcop -Dhttps.port=9443 -Dplay.crypto.secret=9IoC0znSqS4oaSd7KaR3yeoXy4yET3XD

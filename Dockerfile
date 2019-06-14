FROM openjdk:12-oracle
ADD target/universal/traefikcop-1.0-SNAPSHOT.tgz /svc
EXPOSE 9000 9443 5005
RUN chmod +x /svc/traefikcop-1.0-SNAPSHOT/bin/traefikcop
CMD /svc/traefikcop-1.0-SNAPSHOT/bin/traefikcop -Dhttps.port=9443 -Dconfig.file=${CONFIG_FILE_PATH}

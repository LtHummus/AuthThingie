FROM openjdk:12-oracle
ADD target/universal/auththingie-1.0-SNAPSHOT.tgz /svc
EXPOSE 9000 9443 5005
RUN chmod +x /svc/auththingie-1.0-SNAPSHOT/bin/auththingie
CMD /svc/auththingie-1.0-SNAPSHOT/bin/auththingie -Dhttps.port=9443 -Dconfig.file=${CONFIG_FILE_PATH}

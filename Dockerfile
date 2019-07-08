FROM openjdk:12-oracle
ADD target/universal/auththingie-0.0.2.tgz /svc
ADD scripts/generate_totp.sh /usr/local/bin/generate_totp
EXPOSE 9000 9443 5005
RUN chmod +x /svc/auththingie-0.0.2/bin/auththingie
CMD /svc/auththingie-0.0.2/bin/auththingie -Dhttps.port=9443 -Dconfig.file=${AUTHTHINGIE_CONFIG_FILE_PATH}

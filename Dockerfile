FROM openjdk:8
ADD target/universal/auththingie-0.1.0.tgz /svc
ADD scripts/generate_totp.sh /usr/local/bin/generate_totp
EXPOSE 9000
RUN chmod +x /svc/auththingie-0.1.0/bin/auththingie
CMD /svc/auththingie-0.1.0/bin/auththingie


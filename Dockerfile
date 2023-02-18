FROM --platform=$BUILDPLATFORM hseeberger/scala-sbt:11.0.14.1-oraclelinux8_1.6.2_2.13.8

COPY . .
RUN sbt universal:packageZipTarball

FROM amazoncorretto:11
RUN mkdir /svc
COPY --from=0 /root/target/universal/auththingie-0.2.2.tgz /svc
ADD scripts/generate_totp.sh /usr/local/bin/generate_totp
EXPOSE 9000
RUN cd /svc && yum install -y tar gzip && tar xvf auththingie-0.2.2.tgz && chmod +x /svc/auththingie-0.2.2/bin/auththingie
CMD /svc/auththingie-0.2.2/bin/auththingie


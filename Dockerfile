FROM --platform=$BUILDPLATFORM sbtscala/scala-sbt:eclipse-temurin-jammy-17.0.9_9_1.9.7_2.13.12

COPY . .
RUN sbt 'Universal / packageZipTarball'

FROM eclipse-temurin:17
RUN mkdir /svc
COPY --from=0 /root/target/universal/auththingie-0.2.3.tgz /svc
ADD scripts/generate_totp.sh /usr/local/bin/generate_totp
EXPOSE 9000
RUN cd /svc && tar xvf auththingie-0.2.3.tgz && rm auththingie-0.2.3.tgz && chmod +x /svc/auththingie-0.2.3/bin/auththingie
CMD /svc/auththingie-0.2.3/bin/auththingie


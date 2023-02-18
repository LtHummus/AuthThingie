FROM --platform=$BUILDPLATFORM sbtscala/scala-sbt:eclipse-temurin-17.0.5_8_1.8.2_2.13.10

COPY . .
RUN sbt universal:packageZipTarball

FROM eclipse-temurin:17
RUN mkdir /svc
COPY --from=0 /root/target/universal/auththingie-0.2.2.tgz /svc
ADD scripts/generate_totp.sh /usr/local/bin/generate_totp
EXPOSE 9000
RUN cd /svc && tar xvf auththingie-0.2.2.tgz && chmod +x /svc/auththingie-0.2.2/bin/auththingie
CMD /svc/auththingie-0.2.2/bin/auththingie


#!/usr/bin/env bash

sbt test && sbt universal:packageZipTarball && docker build -t lthummus/auththingie . && docker-compose up -d && docker-compose logs -f auth

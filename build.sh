#!/usr/bin/env bash

sbt test && sbt universal:packageZipTarball && docker-compose build && docker-compose up -d && docker-compose logs -f auth

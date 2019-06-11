#!/usr/bin/env bash

sbt universal:packageZipTarball && docker-compose build && docker-compose up -d && docker-compose logs -f auth

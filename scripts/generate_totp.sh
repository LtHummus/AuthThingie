#!/usr/bin/env bash

java -cp '/svc/auththingie-0.2.1/lib/*' util.GenerateTotp "$@"

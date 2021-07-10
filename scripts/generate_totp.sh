#!/usr/bin/env bash

java -cp '/svc/auththingie-0.4.0/lib/*' util.GenerateTotp "$@"

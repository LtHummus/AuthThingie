#!/usr/bin/env bash

java -cp '/svc/auththingie-0.2.0/lib/*' util.GenerateTotp "$@"

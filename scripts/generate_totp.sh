#!/usr/bin/env bash

java -cp '/svc/auththingie-0.1.0/lib/*' util.GenerateTotp "$@"

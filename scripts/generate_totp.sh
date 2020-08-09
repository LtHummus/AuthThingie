#!/usr/bin/env bash

java -cp '/svc/auththingie-0.1.1/lib/*' util.GenerateTotp "$@"

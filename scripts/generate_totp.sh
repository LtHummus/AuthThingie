#!/usr/bin/env bash

java -cp '/svc/auththingie-0.2.3/lib/*' util.GenerateTotp "$@"

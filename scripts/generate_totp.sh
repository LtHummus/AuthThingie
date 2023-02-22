#!/usr/bin/env bash

java -cp '/svc/auththingie-0.2.2/lib/*' util.GenerateTotp "$@"

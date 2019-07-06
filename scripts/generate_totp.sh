#!/usr/bin/env bash

java -cp '/svc/auththingie-1.0-SNAPSHOT/lib/*' services.totp.GeneratorApp "$@"

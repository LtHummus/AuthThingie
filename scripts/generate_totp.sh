#!/usr/bin/env bash

java -cp '/svc/auththingie-0.0.3/lib/*' services.totp.GeneratorApp "$@"

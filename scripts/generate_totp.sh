#!/usr/bin/env bash

java -cp '/svc/auththingie-0.0.4/lib/*' services.totp.GeneratorApp "$@"

#!/usr/bin/env sh

lerna run compile --stream \
&& lerna run lint:fix --stream \
&& lerna run lint:check --stream \
&& lerna run test --stream

#!/usr/bin/env sh

lerna clean --yes \
&& lerna run clean --stream \
&& lerna bootstrap \
&& lerna run compile --stream \
&& lerna run lint:fix --stream \
&& lerna run lint:check --stream \
&& lerna run test --stream

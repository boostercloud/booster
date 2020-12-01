#!/usr/bin/env sh

lerna clean --yes \
&& lerna bootstrap \
&& lerna run clean --stream \
&& lerna run compile --stream \
&& lerna run lint:fix --stream \
&& lerna run lint:check --stream \
&& lerna run test --stream

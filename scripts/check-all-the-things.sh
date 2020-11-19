#!/usr/bin/env sh

lerna clean --yes \
&& lerna run clean --stream \
&& lerna bootstrap \
&& npm i packages/* \
&& npm i \
&& lerna run compile --stream \
&& lerna run lint:fix --stream \
&& lerna run lint:check --stream \
&& lerna run test --stream

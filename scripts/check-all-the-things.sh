#!/usr/bin/env sh
lerna clean --yes && lerna run clean --stream && lerna bootstrap && lerna run compile --stream && lerna run fix-lint --stream && lerna run lint --stream && lerna run test --stream
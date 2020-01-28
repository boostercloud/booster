#!/usr/bin/env sh
lerna clean --yes && lerna run clean && lerna bootstrap && lerna run compile && lerna run fix-lint && lerna run lint && lerna run test
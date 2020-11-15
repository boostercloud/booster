# In theory we could write all of this with Bash style &&,
# but in order to maintain backwards compatibility
# with Powershell <7.0 (what most Windows users have preinstalled)
# we write it in the old-school style
lerna clean --yes
lerna run clean --stream
if ($?) { lerna bootstrap }
if ($?) { lerna run compile --stream }
if ($?) { lerna run fix-lint --stream }
if ($?) { lerna run lint --stream }
if ($?) { lerna run test --stream }
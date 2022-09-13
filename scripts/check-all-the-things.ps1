# In theory we could write all of this with Bash style &&,
# but in order to maintain backwards compatibility
# with Powershell <7.0 (what most Windows users have preinstalled)
# we write it in the old-school style
rush purge
if ($?) { rush install }
if ($?) { rush build }
if ($?) { rush lint:fix }
if ($?) { rush test }
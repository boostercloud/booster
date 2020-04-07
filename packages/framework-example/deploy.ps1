# This script allows to deploy the example application event within the world of lerna packages.

# First, we ensure that the project is bootstrapped, and all the dependencies are installed (node_modules is placed at
# root of the project)
lerna bootstrap

# We are about to install the dependencies for production changing the location of the node_modules, so we first
# rename the node_modules that is in the root of the project (if not, it would be deleted)
Move-Item ../../node_modules ../../node_modules_dev

# Install the dependencies in production mode (inside the example application directory)
yarn install --production --no-bin-links --modules-folder ./node_modules

# Now we undo the name change of the root node_modules. This is needed to compile the project, as:
# - All the other packages don't see the node_modules inside the example app
# - We need the dev dependencies that were not installed in the previous command
Move-Item ../../node_modules_dev ../../node_modules
# Clean and compile the project
lerna run clean
lerna run compile

# Remove non-needed packages (lerna adds them as dependencies)
Remove-Item -Recurse -Force node_modules/@boostercloud/framework-example
Remove-Item -Recurse -Force node_modules/@boostercloud/cli

# Finally invoke the "deploy" command. Thanks to the previous steps, the full example application with its dependencies
# (node_modules) in production mode (which takes less space) will get zipped and deployed to AWS
../cli/bin/run deploy -e production

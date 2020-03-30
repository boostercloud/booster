git clone https://github.com/boostercloud/docs-site .docs
cp -r ./docs/documentation/* ./.docs/source/includes/
cd ./.docs
bundle install
bundle exec middleman build --clean
cd ..
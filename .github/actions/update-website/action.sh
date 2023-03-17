#!/bin/bash

GRAPHQL_URL="http://localhost:3000/graphql"
REPO_URL="https://raw.githubusercontent.com/boostercloud/booster/main/"

# Auth0 credentials from environment variables
CLIENT_ID="$AUTH0_CLIENT_ID"
CLIENT_SECRET="$AUTH0_CLIENT_SECRET"
AUDIENCE="$AUTH0_PRIVATEGPT_AUDIENCE"
AUTH0_DOMAIN="$AUTH0_DOMAIN"

# Go up three directories to get to the parent's parent's parent
cd ../../..

# Define GraphQL query
fetch_pages_query='{
  ListPageReadModels(filter: {}, limit: 1000, sortBy: {}) {
    items {
      id
      path
      title
      checksum
    }
    count
    cursor
  }
}'

# Perform the GraphQL query using curl and store the result in a variable
graphql_result=$(curl -X POST -H "Content-Type: application/json" -d "{\"query\": \"$fetch_pages_query\"}" $GRAPHQL_URL)

# Define array of excluded directories
excluded_dirs=("node_modules" "build" ".docusaurus" "static" "src")

# Iterate through each .md and .mdx file in the website directory and its subdirectories
# Build the find command with excluded directories
find_cmd="find website -type f \( -name \"*.md\" -o -name \"*.mdx\" \)"

for dir in "${excluded_dirs[@]}"; do
  find_cmd+=" -not -path \"*/$dir/*\""
done

# Create an array to store the list of local files
local_files=()


# Pipe the output to head and iterate over the results
eval "$find_cmd" | head -n 1 | while read filepath; do

  # Get the path name, file name, and checksum
  path=$(dirname "$filepath")
  filename=$(basename "$filepath")
  checksum=$(openssl sha256 < "$filepath" | cut -d ' ' -f 2)

  echo "NEW FILE"
  echo "$path"
  echo "$filename"
  echo "$checksum"

  # Add the file to the local files array
  local_files+=("$path/$filename:$checksum")
done


# echo $local_files

# # Create an array to store the list of pages returned from the GraphQL query
# remote_pages=()

# # Parse the result of the GraphQL query to get the list of pages
# jq_result=$(echo "$graphql_result" | jq '.data.ListPageReadModels.items')
# while IFS= read -r line; do
#   id=$(echo "$line" | jq -r '.id')
#   path=$(echo "$line" | jq -r '.path')
#   title=$(echo "$line" | jq -r '.title')
#   checksum=$(echo "$line" | jq -r '.checksum')
#   remote_pages+=("$path/$title:$checksum")
# done <<< "$jq_result"

# echo $remote_pages

# Get Auth0 token
auth0_response=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "{\"client_id\":\"$CLIENT_ID\",\"client_secret\":\"$CLIENT_SECRET\",\"audience\":\"$AUDIENCE\",\"grant_type\":\"client_credentials\"}" \
  $AUTH0_URL)

access_token=$(echo $auth0_response | jq -r '.access_token')

# Perform the GraphQL mutation using curl
graphql_response=$(curl -s -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $access_token" \
  -d "{\"query\": \"mutation { StorePage(input: { path: \\\"$REPO_URL$path\\\", title: \\\"$filename\\\", checksum: \\\"$checksum\\\" }) }\"}" \
  $GRAPHQL_URL)

echo $graphql_response
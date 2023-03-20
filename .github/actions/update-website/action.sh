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

# Get Auth0 token
auth0_response=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "{\"client_id\":\"$CLIENT_ID\",\"client_secret\":\"$CLIENT_SECRET\",\"audience\":\"$AUDIENCE\",\"grant_type\":\"client_credentials\"}" \
  $AUTH0_DOMAIN)

access_token=$(echo $auth0_response | jq -r '.access_token')

# Define GraphQL query
fetch_pages_query='{ListPageReadModels(filter: {}, limit: 1000, sortBy: {}) {items {id path title checksum} count cursor}}'

# Perform the GraphQL query using curl and store the result in a variable
graphql_result=$(curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${access_token}" \
  -d "{\"query\": \"$fetch_pages_query\"}" $GRAPHQL_URL)

printf '%s' "$graphql_result" | jq '.data.ListPageReadModels.items[] | {id, path, title, checksum}'


# Parse the GraphQL result using jq
items=$(echo $graphql_result | jq '.data.ListPageReadModels.items')
count=$(echo $graphql_result | jq '.data.ListPageReadModels.count')
cursor=$(echo $graphql_result | jq '.data.ListPageReadModels.cursor')


remote_file_paths=()
remote_file_checksums=()
remote_file_ids=()

paths=()
checksums=()


echo "Remote Files ========================="

# Loop over each item in the 'items' array
for item in $(echo "${items}" | jq -r '.[] | @base64'); do
  # Decode the base64-encoded JSON for the current item
  _jq() {
    echo "${item}" | base64 --decode | jq -r "${1}"
  }

  # Extract the fields from the current item
  id=$(_jq '.id')
  path=$(_jq '.path')
  title=$(_jq '.title')
  checksum=$(_jq '.checksum')

  echo "$path/$title:$checksum"
  remote_file_ids+=("$id")
  remote_file_paths+=("$path/$title")
  remote_file_checksums+=("$checksum")
done

# Define array of excluded directories
excluded_dirs=("node_modules" "build" ".docusaurus" "static" "src")

# Iterate through each .md and .mdx file in the website directory and its subdirectories
# Build the find command with excluded directories
find_cmd="find website -type f \( -name \"*.md\" -o -name \"*.mdx\" \)"

for dir in "${excluded_dirs[@]}"; do
  find_cmd+=" -not -path \"*/$dir/*\""
done


echo "Local Files ========================="

# Use process substitution to avoid creating a subshell
while read filepath; do
  checksum=$(openssl sha256 < "$filepath" | cut -d ' ' -f 2)

  echo "$REPO_URL$filepath:$checksum"
  paths+=("$REPO_URL$filepath")
  checksums+=("$checksum")
done < <(eval "$find_cmd" | head -n 5)

store_pages=()
remove_pages=()

echo "Find missing files in local files ==============="
# Find missing files in local files
for (( i=0; i<${#remote_file_paths[@]}; i++ )); do
  file="${remote_file_paths[$i]}"
  checksum="${remote_file_checksums[$i]}"
  
  # Check if the file exists in local_file and has the same checksum
  if grep -q -F "${checksum}" <(echo "${checksums[*]}"); then
    # The file exists in both arrays, so do nothing
  :
  else
    remove_pages+=("${file}:${checksum}")

    pageId="${remote_file_ids[$i]}"
    echo "Remove page with id: ${pageId}"

    curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${access_token}"\
  -d "{\"query\": \"mutation { DeletePage(input: { pageID: \\\"$pageId\\\" }) }\"}" \
  $GRAPHQL_URL
  fi
done

echo "Find missing files in remote files ==============="
# Find missing files in remote files
for (( i=0; i<${#paths[@]}; i++ )); do
  file="${paths[$i]}"
  checksum="${checksums[$i]}"
  
  # Check if the file exists in remote_file and has the same checksum
  if grep -q -F "${checksum}" <(echo "${remote_file_checksums[*]}"); then
    # The file exists in both arrays, so do nothing
    :
  else
    store_pages+=("${file}:${checksum}")

    echo "Store page with checksum: ${checksum}"
    path=$(dirname "$file")
    filename=$(basename "$file")

    curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${access_token}"\
  -d "{\"query\": \"mutation { StorePage(input: { path: \\\"$path\\\", title: \\\"$filename\\\", checksum: \\\"$checksum\\\" }) }\"}" \
  $GRAPHQL_URL
  fi
done


# Print the results
echo "Local files added:"
echo "${#store_pages[@]}"

for (( i=0; i<${#store_pages[@]}; i++ )); do
  echo -e "${store_pages[$i]}"
done

echo -e "Remote files deleted:"
echo "${#remove_pages[@]}"

for (( i=0; i<${#remove_pages[@]}; i++ )); do
  echo -e "${remove_pages[$i]}"
done


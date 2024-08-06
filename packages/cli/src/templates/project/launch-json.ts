
export const template = '{
    "version": "0.2.0",
    "configurations": [
      // Runs npx boost -e local
      {
        "command": "npm run build && npx boost start -e local",
        "name": "Start Debug Server",
        "request": "launch",
        "type": "node-terminal",
        "cwd": "${workspaceFolder}"
      },
    ]
  }'
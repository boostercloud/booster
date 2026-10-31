import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const baseDir = path.join(__dirname, "..");
const outputFile = path.join(baseDir, "dist/repo-mixing.js");

// Create output directory if missing
try {
  if (!fs.existsSync(path.dirname(outputFile))) {
    fs.mkdirSync(path.dirname(outputFile), { recursive: true });
  }
} catch (error) {
  console.error("Error creating output directory, skipping...", error);
}

// Clear existing file
try {
  fs.writeFileSync(outputFile, "", "utf8");
} catch (error) {
  console.error("Error clearing output file, skipping...", error);
}

// Search for all projects
const getDirectoriesWithPackageJson = (dirPath) => {
  let directories = [];
  try {
    const items = fs.readdirSync(dirPath, { withFileTypes: true });

    items.forEach((item) => {
      const itemPath = path.join(dirPath, item.name);
      if (item.isDirectory()) {
        const packageJsonPath = path.join(itemPath, "package.json");
        if (fs.existsSync(packageJsonPath)) {
          directories.push(itemPath);
        } else {
          directories = directories.concat(getDirectoriesWithPackageJson(itemPath));
        }
      }
    });
  } catch (error) {
    console.error(`Error reading directory: ${dirPath}, skipping...`);
  }
  return directories;
};

// Combine .js files
const appendJsFilesToCombined = (sourceDir) => {
  try {
    const items = fs.readdirSync(sourceDir, { withFileTypes: true });

    items.forEach((item) => {
      const sourcePath = path.join(sourceDir, item.name);
      if (item.isDirectory()) {
        appendJsFilesToCombined(sourcePath);
      } else if (path.extname(item.name) === ".js") {
        console.log(`Appending from: ${sourcePath}`);

        try {
          let fileContent = fs.readFileSync(sourcePath, "utf8");
          fs.appendFileSync(outputFile, `\n// Start of ${sourcePath}\n`);
          fs.appendFileSync(outputFile, fileContent);
          fs.appendFileSync(outputFile, `\n// End of ${sourcePath}\n`);
        } catch (error) {
          console.error(`Error reading file: ${sourcePath}, skipping...`);
        }
      }
    });
  } catch (error) {
    console.error(`Error processing directory: ${sourceDir}, skipping...`);
  }
};

// Log the found directories
const allDirsWithPackageJson = getDirectoriesWithPackageJson(baseDir);
console.log("Directories with 'package.json':", allDirsWithPackageJson);

// Log an error if no projects found
if (allDirsWithPackageJson.length === 0) {
  console.error("⚠️ No directories with 'package.json' found. Continuing anyway...");
}

// Handle merging from each directory
allDirsWithPackageJson.forEach((projectDir) => {
  console.log(`Merging files from: ${projectDir}`);
  appendJsFilesToCombined(projectDir);
  console.log(`Merged: ${projectDir}`);
});

// Log the output file when merging complete.
console.log("Mixing complete. All .js files are combined into:", outputFile);


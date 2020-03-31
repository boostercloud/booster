before(() => {
  console.log("Here we run the actual deployment using the CLI")
})

after(() => {
  console.log("Here we nuke the project using the CLI")
})
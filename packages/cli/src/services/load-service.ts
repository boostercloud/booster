import { BoosterApp } from '@boostercloud/framework-types'
import * as path from 'path'

export function loadUserProject(userProjectPath: string): { Booster: BoosterApp } {
    const projectIndexJSPath = path.resolve(path.join(userProjectPath, 'dist', 'index.js'))
    console.log("loadUserProject real function called")
    return require(projectIndexJSPath)
}
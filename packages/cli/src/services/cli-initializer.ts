import { existsSync, mkdirSync } from 'fs'
import * as path from 'path'
const os = require('os')

export async function createBoosterHomeFolder(): Promise<void> {
    const boosterHomeDir = path.join(os.homedir(), '.booster')
    try {
        if (!existsSync(boosterHomeDir)) {
            mkdirSync(boosterHomeDir, {})
        }
    } catch {}    
}
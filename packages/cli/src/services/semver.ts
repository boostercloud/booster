export default class Semver {
    versionParts: number[]
        
    constructor(semverString: string) {
        this.versionParts = semverString.split('.').map((v) => parseInt(v,10))
        if (this.versionParts.length !== 3) {
          throw new Error(`Versions must follow semantic convention X.Y.Z | current version: ${semverString}.`)
        }        
    }

    public equalsInBreakingSection(version: Semver): boolean {
        return this.versionParts[0] === version.versionParts[0]
    }

    public equalsInFeatureSection(version: Semver): boolean {
        return this.versionParts[1] === version.versionParts[1]
    }

    public equalsInFixSection(version: Semver): boolean {
        return this.versionParts[2] === version.versionParts[2]
    }

    public equals(version: Semver): boolean {
        return this.equalsInBreakingSection(version) && this.equalsInFeatureSection(version) && this.equalsInFixSection(version)
    }

    public greaterInBreakingSectionThan(version: Semver): boolean {
        return this.versionParts[0] > version.versionParts[0]
    }

    public greaterInFeatureSectionThan(version: Semver): boolean {
        return this.versionParts[1] > version.versionParts[1]
    }
}
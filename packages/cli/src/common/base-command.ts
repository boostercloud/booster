import 'reflect-metadata'
import { Command, flags } from '@oclif/command'
import { IConfig } from '@oclif/config'
import { CloudProvider } from '../services/cloud-provider'
import { GenericCloudProvider } from '../services/cloud-provider/generic.impl'
import { DynamicImporter } from '../services/dynamic-importer'
import { SimpleDynamicImporter } from '../services/dynamic-importer/simple.impl'
import { ErrorHandler } from '../services/error-handler'
import { CliErrorHandler } from '../services/error-handler/cli.impl'
import { FileGenerator } from '../services/file-generator'
import { FileLogger } from '../services/logger/file.impl'
import { ContainerBuilder } from 'diod'
import { AnyClass, Class, Level, Logger } from '@boostercloud/framework-types'
import { LocalFileGenerator } from '../services/file-generator/local'
import { OraLogger } from '../services/logger/ora.impl'
import { FileSystem } from '../services/file-system'
import { LocalFileSystem } from '../services/file-system/local.impl'
import { inferPackageManager } from '../services/package-manager/factory'
import { PackageManager } from '../services/package-manager'
import { UserInput } from '../services/user-input'
import { ConsoleUserInput } from '../services/user-input/console.impl'
import { Process } from '../services/process'
import { LocalProcess } from '../services/process/local.impl'
import { UserProject } from '../services/user-project'
import { LocalUserProject } from '../services/user-project/local.impl'
import { IBooleanFlag, IOptionFlag } from '@oclif/parser/lib/flags'

export abstract class BaseCommand<T extends typeof Command> extends Command {
  static baseFlags = {
    help: flags.help({ char: 'h' }),
    verbose: flags.boolean({
      description: 'show extra debugging information',
      default: false,
    }),
    silent: flags.boolean({
      description: 'log all output into ./errors.log',
      default: false,
    }),
    level: flags.string({
      description: 'log level',
      default: 'info',
    }),
  }

  protected flags!: Flags<T>
  protected logLevel!: Level
  protected args!: Args<T>
  abstract implementation: CliCommandImplementation<T>
  private containerBuilder!: ContainerBuilder

  public async init(): Promise<void> {
    await super.init()
    const { args, flags } = await this.parse(this.ctor)
    const levelString = flags.level as keyof typeof Level
    this.logLevel = Level[levelString]
    this.flags = flags as Flags<T>
    this.args = args as Args<T>
    await this.initBuilder()
  }

  private async initBuilder(): Promise<void> {
    this.containerBuilder = new ContainerBuilder()

    // We register the logger first as its the minimum required to run the rest of the services
    if (this.flags.verbose) {
      this.logLevel = Level.debug
    }
    if (this.flags.silent) {
      this.containerBuilder.register(Logger).useFactory(() => new FileLogger(this.logLevel))
    } else {
      this.containerBuilder.register(Logger).useFactory(() => new OraLogger(this.logLevel))
    }

    // Now we register filesystem and process in order to run the package manager factory
    this.containerBuilder.register(FileSystem).use(LocalFileSystem)
    this.containerBuilder.register(Process).use(LocalProcess)

    // We build a container to run the package manager factory, this one will be discarded
    const InferredPackageManager = await inferPackageManager(this.containerBuilder.build())

    // We register the rest of the services
    this.containerBuilder.register(PackageManager).use(InferredPackageManager)
    this.containerBuilder.register(CloudProvider).use(GenericCloudProvider)
    this.containerBuilder.register(DynamicImporter).use(SimpleDynamicImporter)
    this.containerBuilder.register(ErrorHandler).use(CliErrorHandler)
    this.containerBuilder.register(FileGenerator).use(LocalFileGenerator)
    this.containerBuilder.register(UserInput).use(ConsoleUserInput)
    this.containerBuilder.register(UserProject).use(LocalUserProject)
  }

  protected async runImplementation(implementationClass: CliCommandImplementation<T>): Promise<void> {
    this.containerBuilder.registerAndUse(implementationClass)
    const container = this.containerBuilder.build()
    const userProject = container.get(UserProject)
    userProject.cliVersion = this.config.version
    const implementationInstance = container.get<ImplementationInstance<T>>(implementationClass)
    const errorHandlerInstance = container.get(ErrorHandler)
    try {
      await implementationInstance.run(this.flags, this.args, this.config)
    } catch (error) {
      await errorHandlerInstance.handleError(error)
    }
  }

  async run(): Promise<void> {
    await this.runImplementation(this.implementation)
  }

  protected async catch(err: Error & { exitCode?: number }): Promise<unknown> {
    // add any custom logic to handle errors from the command
    // or simply return the parent class error handling
    return super.catch(err)
  }

  protected async finally(_: Error | undefined): Promise<unknown> {
    // called after run and catch regardless of whether or not the command errored
    return super.finally(_)
  }
}

interface ImplementationInstance<T extends typeof Command> {
  run: (flags: Flags<T>, args: Args<T>, config: IConfig) => Promise<void>
}

type CliCommandImplementation<T extends typeof Command> = Class<ImplementationInstance<T>>

/** Decorator to ensure that the implementation class for a CLI command is runnable */
export function CliCommand() {
  return <T extends AnyClass>(target: T): T => {
    return target
  }
}

/**
 * This type is used to extract the flags from an Oclif command class.
 */
export type Flags<T> = T extends { flags: unknown }
  ? {
      [P in keyof T['flags']]: OclifToPrimitive<T['flags'][P]>
    } &
      {
        [P in keyof typeof BaseCommand.baseFlags]?: OclifToPrimitive<typeof BaseCommand.baseFlags[P]>
      }
  : never

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type OclifToPrimitive<T> = T extends IBooleanFlag<any> ? boolean : T extends IOptionFlag<infer U> ? U : never

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export type Args<T> = { [x: string]: string | undefined }

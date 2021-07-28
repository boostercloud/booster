import { BoosterConfig, Class, ReadModelInterface } from "framework-types/dist";
import { Booster } from "..";

/**
 * Decorator that's appliable to a read model constructor parameter to declare a foreign key reference to a different read model
 */
export function References(
    referencedReadModelClassParam: Class<ReadModelInterface>?
): (readModelClass: Class<ReadModelInterface>, propertyName: string) => void {
  return (readModelClass: Class<ReadModelInterface>, propertyName: string) => {
    Booster.configureCurrentEnv((config) => {
      initializeConfigForReadModelReferences(config, readModelClass);

      // If the referenced class is not present we try to infer the class name from the property name
      const referencedReadModelClass = referencedReadModelClassParam ?? inferReferencedReadModelClass(readModelClass, propertyName)
            
      checkForDuplicateReferences(config, readModelClass, referencedReadModelClass, propertyName);
      
      config.readModelReferences[readModelClass.name].push({
        referencedReadModel: referencedReadModelClass,
        foreignKey: propertyName
      })
    })
  }
}

function initializeConfigForReadModelReferences(config: BoosterConfig, readModelClass: Class<ReadModelInterface>) {
  if (!config.readModelReferences[readModelClass.name]) {
    config.readModelReferences[readModelClass.name] = [];
  }
}

function inferReferencedReadModelClass(currentReadModel: Class<ReadModelInterface>, propertyName: string): Class<ReadModelInterface> {
  const propertyNameWithoutId = propertyName.split(/id$/i)[0]
  const potentialClassName = propertyNameWithoutId.charAt(0).toUpperCase() + propertyNameWithoutId.slice(1)
  const fail = () => { 
    throw new Error(
      `Tried to infer the referenced read model class from ${currentReadModel.name} ` +
      `using the foreign key name ${propertyName}, but ${potentialClassName} doesn't ` + 
      `seem to be a class decorated with the '@ReadModel' decorator. Please check ` +
      `the documentation for the '@References' decorator to make sure you're properly using it.`
    ) 
  }
  try {
    const klass = eval(potentialClassName)
    if (Reflect.hasOwnMetadata("custom:ReadModel", klass)) {
      return klass
    } else fail()
  } catch (e) {
    fail()
  }
}

function checkForDuplicateReferences(config: BoosterConfig, readModelClass: Class<ReadModelInterface>, referencedReadModelClass: Class<ReadModelInterface>, propertyName: string) {
  if (config.readModelReferences[readModelClass.name].find(
    (entry: ReadModelReferenceMetadata) => entry.referencedReadModel === referencedReadModelClass && entry.foreignKey === propertyName)) {
    throw new Error(
      `Tried to register a read model relationship from ${readModelClass.name} to ${referencedReadModelClass} using the ` +
      `foreignKey ${propertyName}, but it already existed.`
    );
  }
}
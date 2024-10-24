import { Projects, ReadModel } from '@boostercloud/framework-core'
import { UserWithEmail } from '../roles'
import {
  ProjectionInfo,
  ProjectionInfoReason,
  ProjectionResult,
  ReadModelAction,
  UUID,
} from '@boostercloud/framework-types'
import { Product, ProductType } from '../entities/product'
import { Money } from '../common/money'
import { Pack } from '../entities/pack'

// This is an example read model for a possible admin-exclusive report to show last and previous updates to products
@ReadModel({
  authorize: [UserWithEmail],
})
export class ProductReadModel {
  public constructor(
    readonly id: UUID,
    readonly sku: string,
    readonly displayName: string,
    readonly description: string,
    readonly availability: number,
    public deleted: boolean,
    readonly productDetails: Record<string, unknown>,
    readonly productType: ProductType = 'Other',
    readonly price?: Money,
    readonly packs?: Array<Pack>
  ) {}

  @Projects(Product, 'id', ProductReadModel.unProjectWithProduct)
  public static updateWithProduct(product: Product): ProjectionResult<ProductReadModel> {
    if (product.deleted) {
      return ReadModelAction.Delete
    } else {
      return new ProductReadModel(
        product.id,
        product.sku,
        product.displayName,
        product.description,
        product.availability,
        product.deleted,
        product.productDetails,
        product.productType,
        product.price,
        []
      )
    }
  }

  public static unProjectWithProduct(
    _product: Product,
    _currentProductReadModel?: ProductReadModel,
    _projectionInfo?: ProjectionInfo
  ): ProjectionResult<ProductReadModel> {
    return ReadModelAction.Delete
  }

  @Projects(Pack, 'products', ProductReadModel.updateWithPack)
  public static updateWithPack(
    pack: Pack,
    readModelID: UUID,
    currentProductReadModel?: ProductReadModel,
    projectionInfo?: ProjectionInfo
  ): ProjectionResult<ProductReadModel> {
    if (projectionInfo?.reason === ProjectionInfoReason.ENTITY_DELETED) {
      return ReadModelAction.Delete
    }
    if (!currentProductReadModel) {
      return ReadModelAction.Nothing
    } else {
      let packList = currentProductReadModel.packs || []
      if (packList?.find((existingPack) => existingPack.id == pack.id)) {
        //Update existing pack in case new products added
        packList = packList.map((existingPack) => (existingPack.id == pack.id ? pack : existingPack))
      } else {
        //Add pack to existing packs
        packList?.push(pack)
      }
      return new ProductReadModel(
        currentProductReadModel.id,
        currentProductReadModel.sku,
        currentProductReadModel.displayName,
        currentProductReadModel.description,
        currentProductReadModel.availability,
        currentProductReadModel.deleted,
        currentProductReadModel.productDetails,
        currentProductReadModel.productType,
        currentProductReadModel.price,
        packList
      )
    }
  }
}

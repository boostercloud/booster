import chalk from 'chalk'

/**
 * Class to output text with proper Booster brand colors
 */
export default class Brand {
  /**
   * Apply a dangerous feeling (red) to some text
   * @param text
   */
  public static dangerize(text: string): string {
    return chalk.hex('#FA6E59')(text)
  }

  /**
   * Apply a mellancholic blue color to some text
   * @param text
   */
  public static mellancholize(text: string): string {
    return chalk.hex('#4797D8')(text)
  }

  /**
   * Apply a nice canary yellow tone to some text
   * @param text
   */
  public static canarize(text: string): string {
    return chalk.hex('#FFD243')(text)
  }

  /**
   * Apply an energic orange tone to some text
   * @param text
   */
  public static energize(text: string): string {
    return chalk.hex('#FF8D40')(text)
  }
}

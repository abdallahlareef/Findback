export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

/** FR-10 tiers as presented to users (from the "Verification strength adapts to item risk" slide). */
export const RISK_LEVELS: { value: RiskLevel; label: string; examples: string; policy: string }[] = [
  { value: 'LOW', label: 'Low risk', examples: 'umbrella, bottle, notebook', policy: 'One private characteristic may be sufficient for admin review.' },
  { value: 'MEDIUM', label: 'Medium risk', examples: 'earbuds, calculator, watch', policy: 'Multiple characteristics are used and attempts are limited.' },
  { value: 'HIGH', label: 'High risk', examples: 'laptop, smartphone, wallet', policy: 'Stronger evidence and a mandatory Security/Admin review are required.' },
]

/**
 * Fixed categories for the report form, each with its risk level. Mirrors RiskTier.FORM_CATEGORIES
 * in the backend; RiskTierTest checks every entry. Using the same words on lost and found reports
 * also makes candidate matching (exact category comparison) work reliably.
 */
export const ITEM_CATEGORIES: { value: string; risk: RiskLevel }[] = [
  { value: 'Phone', risk: 'HIGH' },
  { value: 'Laptop', risk: 'HIGH' },
  { value: 'Tablet', risk: 'HIGH' },
  { value: 'Wallet or purse', risk: 'HIGH' },
  { value: 'ID or student card', risk: 'HIGH' },
  { value: 'Bank or payment card', risk: 'HIGH' },
  { value: 'Passport', risk: 'HIGH' },
  { value: 'Earbuds or headphones', risk: 'MEDIUM' },
  { value: 'Calculator', risk: 'MEDIUM' },
  { value: 'Watch', risk: 'MEDIUM' },
  { value: 'Camera', risk: 'MEDIUM' },
  { value: 'Charger or power bank', risk: 'MEDIUM' },
  { value: 'Keys', risk: 'MEDIUM' },
  { value: 'Glasses', risk: 'MEDIUM' },
  { value: 'Bag or backpack', risk: 'MEDIUM' },
  { value: 'Water bottle', risk: 'LOW' },
  { value: 'Umbrella', risk: 'LOW' },
  { value: 'Notebook or book', risk: 'LOW' },
  { value: 'Stationery', risk: 'LOW' },
  { value: 'Clothing', risk: 'LOW' },
  { value: 'Lunch box', risk: 'LOW' },
]

/** Dropdown value that reveals a free-text "describe the item type" field. */
export const OTHER_CATEGORY = 'Other'

const ORDER: RiskLevel[] = ['LOW', 'MEDIUM', 'HIGH']

export function riskRank(level: RiskLevel): number {
  return ORDER.indexOf(level)
}

export function riskLabel(level: RiskLevel): string {
  return RISK_LEVELS.find((l) => l.value === level)?.label ?? level
}

// Mirrors RiskTier.fromCategory in the backend (com.findback.module3.domain.RiskTier). Keep in sync.
const MEDIUM_AUDIO = ['EARBUD', 'EARPHONE', 'HEADPHONE', 'HEADSET', 'AIRPOD']
const HIGH_PARTS = ['PHONE', 'MOBILE', 'LAPTOP', 'COMPUTER', 'MACBOOK', 'TABLET', 'IPAD', 'WALLET', 'PURSE', 'PASSPORT', 'PAYMENT', 'CREDIT', 'DEBIT']
const HIGH_WORDS = ['CARD']
const MEDIUM_PARTS = ['CALCULATOR', 'WATCH', 'ACCESSORY', 'CAMERA', 'SPEAKER', 'POWERBANK', 'POWER BANK', 'CHARGER']
const LOW_WORDS = ['BOTTLE', 'UMBRELLA', 'NOTEBOOK', 'STATIONERY', 'BOOK', 'PEN', 'PENCIL', 'JACKET', 'HOODIE', 'SCARF', 'CAP', 'HAT', 'MUG', 'LUNCHBOX']

/**
 * Suggested level for a free-text category, or null when the category gives no clear signal
 * (the backend then uses MEDIUM as the minimum).
 */
export function suggestRiskLevel(category: string): RiskLevel | null {
  const words = category.toUpperCase().replace(/[^A-Z0-9]+/g, ' ').trim()
  if (!words) return null
  const text = ` ${words} `
  const containsAny = (parts: string[]) => parts.some((p) => text.includes(p))
  const hasWord = (list: string[]) => list.some((w) => text.includes(` ${w} `) || text.includes(` ${w}S `))
  if (containsAny(MEDIUM_AUDIO)) return 'MEDIUM'
  if (containsAny(HIGH_PARTS) || hasWord(HIGH_WORDS)) return 'HIGH'
  if (containsAny(MEDIUM_PARTS)) return 'MEDIUM'
  if (hasWord(LOW_WORDS)) return 'LOW'
  return null
}

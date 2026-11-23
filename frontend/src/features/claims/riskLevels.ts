export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

/** FR-10 tiers as presented to users (from the "Verification strength adapts to item risk" slide). */
export const RISK_LEVELS: { value: RiskLevel; label: string; examples: string; policy: string }[] = [
  { value: 'LOW', label: 'Low risk', examples: 'umbrella, bottle, notebook', policy: 'One private characteristic may be sufficient for admin review.' },
  { value: 'MEDIUM', label: 'Medium risk', examples: 'earbuds, calculator, watch', policy: 'Multiple characteristics are used and attempts are limited.' },
  { value: 'HIGH', label: 'High risk', examples: 'laptop, smartphone, wallet', policy: 'Stronger evidence and a mandatory Security/Admin review are required.' },
]

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

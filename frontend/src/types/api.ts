export type UserRole = "USER" | "ADMIN";
export type ShoppingType = "DAILY" | "WEEKLY" | "MONTHLY" | "CUSTOM";
export type StockAvailability = "AVAILABLE" | "UNAVAILABLE" | "UNKNOWN";
export type ContributionStatus = "PENDING" | "APPROVED" | "REJECTED";
export type RecommendationStatus = "COMPLETE_STORE_FOUND" | "NO_COMPLETE_STORE";
export type PremiumSource = "SUBSCRIPTION" | "TRIAL" | "ADMIN" | "NONE";
export type BillingCycle = "MONTHLY" | "ANNUAL";
/** Free-plan limits the API reports in a PLAN_LIMIT problem. */
export type PlanLimit = "SHOPPING_LISTS" | "LIST_ITEMS" | "ACTIVE_ALERTS" | "PRICE_HISTORY";

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiProblem {
  title?: string;
  detail?: string;
  status?: number;
  code?: string;
  limit?: PlanLimit;
  path?: string;
  timestamp?: string;
  errors?: Array<{ field: string; message: string }>;
}

export interface User {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  emailVerified: boolean;
  subscriber: boolean;
  premium: boolean;
  premiumSource: PremiumSource;
  trialEndsAt: string | null;
  trialAvailable: boolean;
  createdAt: string;
  updatedAt: string;
}

/** How much of a comparison the plan shows. `visibleStoreLimit` null means every store. */
export interface ComparisonAccess {
  premium: boolean;
  visibleStoreLimit: number | null;
  lockedStores: number;
  dailyComparisonsUsed: number | null;
  dailyComparisonsLimit: number | null;
  dailyLimitReached: boolean;
}

export interface SubscriptionStatus {
  premium: boolean;
  premiumSource: PremiumSource;
  trialEndsAt: string | null;
  trialAvailable: boolean;
  preferredBillingCycle: BillingCycle | null;
  freeLimits: {
    shoppingLists: number;
    listItems: number;
    activeAlerts: number;
    dailyComparisons: number;
    visibleStores: number;
  };
  usage: { shoppingLists: number; activeAlerts: number; comparisonsToday: number };
}

/** Savings from splitting one of the person's lists between stores; fields are null when there is none. */
export interface PremiumValue {
  listName: string | null;
  savings: number | null;
  storeCount: number;
}

export interface CatalogItemHistory {
  itemId: string;
  since: string;
  stores: Array<{
    storeId: string;
    storeName: string;
    points: Array<{ day: string; price: number }>;
  }>;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface State {
  id: string;
  name: string;
  code: string;
}

export interface City {
  id: string;
  stateId: string;
  name: string;
}

export interface Product {
  id: string;
  gtin: string | null;
  name: string;
  brand: string | null;
  description: string | null;
  unit: string | null;
  quantity: number | null;
  category: string | null;
  packageDescription: string | null;
  imageUrl: string | null;
  originUrl: string | null;
  sourceId: string;
  sourceReference: string;
  collectedAt: string;
  updatedAt: string;
}

export interface Store {
  id: string;
  supermarketChainId: string;
  cityId: string;
  name: string;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  active: boolean;
  sourceId: string;
  sourceReference: string;
  collectedAt: string;
  updatedAt: string;
  lastPriceCollectedAt: string | null;
  priceSourceNote: string | null;
}

export interface PriceRecord {
  sourceProductReference: string | null;
  sourceProductName: string | null;
  salesChannel: "ONLINE" | "PHYSICAL_FLYER" | "UNSPECIFIED";
  id: string;
  productId: string;
  storeId: string;
  regularPrice: number;
  promotionalPrice: number | null;
  currency: string;
  availability: StockAvailability;
  collectedAt: string;
  recordedAt: string;
  validUntil: string | null;
  promotionValidUntil: string | null;
  promotionCondition: string | null;
  sourceId: string;
  sourceReference: string;
  originType: "SOURCE" | "USER_CONTRIBUTION";
  originUrl: string | null;
  contributionId: string | null;
}

export interface PriceQuote {
  status: "KNOWN" | "EXPIRED" | "OUT_OF_STOCK" | "NO_OBSERVATION";
  availability: StockAvailability;
  unitPrice: number | null;
  promotionApplied: boolean;
  expiresAt: string | null;
  observation: PriceRecord | null;
}

export interface ProductComparison {
  possibleMatches: Product[];
  productId: string;
  productName: string;
  cityId: string;
  currency: string;
  comparedAt: string;
  stores: PageResponse<{
    storeId: string;
    storeName: string;
    price: PriceQuote;
    measurementPrice: MeasurementPrice | null;
    matchedProduct: Product | null;
    priceSourceNote: string | null;
  }>;
  access: ComparisonAccess;
}

export interface MeasurementPrice {
  amount: number;
  unit: string;
}

export interface ProductDiscoveryResult {
  displayName: string;
  product: Product;
  offers: ProductComparison["stores"]["content"];
  catalogEntries: number;
  identityConfirmed: boolean;
}

export interface ProductOffers {
  productId: string;
  offers: ProductComparison["stores"]["content"];
}

export interface ShoppingListSummary {
  id: string;
  name: string;
  shoppingType: ShoppingType;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ShoppingListItem {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  catalogItemId: string | null;
  imageUrl: string | null;
}

/** A real-world product ("Coca-Cola 2 L") grouped across every store that sells it. */
export interface CatalogItem {
  id: string;
  name: string;
  brand: string | null;
  sizeLabel: string;
  category: string | null;
  imageUrl: string | null;
  productId: string;
  storeCount: number;
  pricedStores: number;
  lowestPrice: number | null;
  highestPrice: number | null;
  lowestPriceStore: string | null;
  pricesCollectedAt: string | null;
}

export interface CatalogItemDetail {
  item: CatalogItem;
  offers: {
    storeId: string;
    storeName: string;
    productId: string | null;
    storeProductName: string | null;
    originUrl: string | null;
    price: PriceQuote;
  }[];
  /** Stores in the comparison without a current price for this item. */
  storesWithoutPrice: number;
  access: ComparisonAccess;
}

export interface ShoppingList extends ShoppingListSummary {
  items: ShoppingListItem[];
}

export interface ShoppingComparisonItem {
  matchedProduct: Product | null;
  hasPossibleMatches: boolean;
  productId: string;
  productName: string;
  quantity: number;
  price: PriceQuote;
  lineTotal: number | null;
}

export interface ShoppingStoreComparison {
  priceSourceNote: string | null;
  storeId: string;
  storeName: string;
  requestedItems: number;
  pricedItems: number;
  missingItems: number;
  subtotalKnown: number | null;
  completeShoppingList: boolean;
  items: ShoppingComparisonItem[];
}

export interface ShoppingListComparison {
  shoppingListId: string;
  cityId: string;
  currency: string;
  comparedAt: string;
  stores: PageResponse<ShoppingStoreComparison>;
  access: ComparisonAccess;
}

export interface StoreRecommendationCandidate {
  storeId: string;
  storeName: string;
  total: number | null;
  requestedItems: number;
  pricedItems: number;
  missingItems: number;
  missingProductIds: string[];
  completeShoppingList: boolean;
  stockUncertain: boolean;
}

export interface ShoppingRecommendation {
  shoppingListId: string;
  cityId: string;
  currency: string;
  comparedAt: string;
  evaluatedStores: number;
  status: RecommendationStatus;
  recommendation: StoreRecommendationCandidate | null;
  closestMatches: StoreRecommendationCandidate[];
  combination: ShoppingCombination;
  /** Buying each item where it is cheapest versus everything at the best single store. */
  splitSavings: {
    storeId: string;
    storeName: string;
    comparedItems: number;
    savings: number;
  } | null;
}

export interface ProductSearchFacets {
  brands: string[];
  categories: string[];
  measurements: Array<{ unit: string; quantity: number }>;
  markets: Array<{ id: string; name: string }>;
}

export interface ShoppingCombination {
  requestedItems: number;
  pricedItems: number;
  missingProductIds: string[];
  subtotalKnown: number | null;
  completeShoppingList: boolean;
  savingsAgainstCompleteStore: number | null;
  stores: Array<{
    storeId: string;
    storeName: string;
    subtotal: number;
    items: ShoppingComparisonItem[];
  }>;
  /** On the free plan the stores are hidden; only totals and `storeCount` remain. */
  locked: boolean;
  storeCount: number;
}

export interface StoreProduct {
  product: Product;
  price: PriceQuote;
}

export interface PriceAlert {
  id: string;
  productId: string;
  cityId: string;
  targetPrice: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  lastNotifiedAt: string | null;
}

export interface AlertNotification {
  id: string;
  alertId: string;
  priceRecordId: string;
  storeId: string;
  unitPrice: number;
  createdAt: string;
  readAt: string | null;
}

export interface UserPreference {
  preferredCityId: string | null;
  favoriteStoreIds: string[];
}

export interface PriceContribution {
  id: string;
  contributorId: string;
  productId: string;
  storeId: string;
  regularPrice: number;
  promotionalPrice: number | null;
  currency: string;
  observedAt: string;
  submittedAt: string;
  validUntil: string | null;
  promotionValidUntil: string | null;
  availability: StockAvailability;
  status: ContributionStatus;
  moderatorId: string | null;
  decidedAt: string | null;
  rejectionReason: string | null;
  priceRecordId: string | null;
}

export interface AdminAudit {
  id: string;
  actorUserId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  occurredAt: string;
}

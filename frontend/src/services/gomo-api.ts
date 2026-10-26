import { apiRequest, queryString } from "@/lib/api";
import type {
  AdminAudit,
  AlertNotification,
  AuthResponse,
  BillingCycle,
  CatalogItem,
  CatalogItemDetail,
  CatalogItemHistory,
  DealsPage,
  ListGoodPrices,
  ListSubstitutes,
  Purchase,
  RecurringList,
  SavingsReport,
  Trip,
  City,
  CollectionHealth,
  PageResponse,
  PremiumValue,
  PriceAlert,
  CreatePriceAlertInput,
  PriceContribution,
  PriceRecord,
  Product,
  ProductSearchFacets,
  ProductComparison,
  ProductOffers,
  ProductDiscoveryResult,
  ShoppingList,
  ShoppingListComparison,
  ShoppingListItem,
  ShoppingRecommendation,
  ShoppingListSummary,
  State,
  StockAvailability,
  Store,
  StoreProduct,
  SubscriptionStatus,
  User,
  UserPreference,
} from "@/types/api";

export interface ProductFilters {
  query?: string;
  brand?: string;
  gtin?: string;
  category?: string;
  page?: number;
  size?: number;
  storeId?: string;
  unit?: string;
  quantity?: number;
  sort?: string;
}

export const authApi = {
  login: (input: { email: string; password: string }) =>
    apiRequest<AuthResponse>("/auth/login", {
      method: "POST",
      body: input,
      authenticated: false,
    }),
  /** 202 sem corpo e sem sessão, exista ou não conta com o e-mail (o dono recebe um aviso). */
  register: (input: { name: string; email: string; password: string }) =>
    apiRequest<void>("/auth/register", {
      method: "POST",
      body: input,
      authenticated: false,
    }),
  logout: () => apiRequest<void>("/auth/logout", { method: "POST" }),
  /** Encerra todas as sessões da conta, inclusive esta. */
  logoutEverywhere: () => apiRequest<void>("/auth/logout-all", { method: "POST" }),
  /** Esta sessão continua; as dos outros aparelhos terminam. */
  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<void>("/auth/password", { method: "POST", body: { currentPassword, newPassword } }),
  /** Usuário da sessão atual, ou undefined (204) para quem não entrou. */
  session: () => apiRequest<User | undefined>("/auth/session", { authenticated: false }),
  /** Troca o token antigo do localStorage por um cookie de sessão. */
  moveSessionToCookie: (legacyToken: string) =>
    apiRequest<User>("/auth/session", {
      method: "POST",
      headers: { Authorization: `Bearer ${legacyToken}` },
      authenticated: false,
    }),
  deleteAccount: (password: string) =>
    apiRequest<void>("/users/me", { method: "DELETE", body: { password } }),
  currentUser: () => apiRequest<User>("/users/me"),
  /** Tudo o que o Gomo guarda sobre a conta, para baixar (LGPD). */
  exportData: () => apiRequest<Record<string, unknown>>("/users/me/export"),
  updateUser: (name: string) =>
    apiRequest<User>("/users/me", { method: "PATCH", body: { name } }),
  requestEmailVerification: () =>
    apiRequest<void>("/auth/email-verifications", { method: "POST" }),
  confirmEmail: (token: string) =>
    apiRequest<void>("/auth/email-verifications/confirm", {
      method: "POST",
      body: { token },
      authenticated: false,
    }),
  requestPasswordReset: (email: string) =>
    apiRequest<void>("/auth/password-resets", {
      method: "POST",
      body: { email },
      authenticated: false,
    }),
  resetPassword: (token: string, password: string) =>
    apiRequest<void>("/auth/password-resets/confirm", {
      method: "POST",
      body: { token, password },
      authenticated: false,
    }),
};

export const catalogApi = {
  catalogItems: (
    filters: { query?: string; cityId?: string; storeIds?: string[]; page?: number; size?: number },
    signal?: AbortSignal,
  ) =>
    apiRequest<PageResponse<CatalogItem>>(
      `/catalog/items?${queryString({ ...filters, storeIds: filters.storeIds?.join(",") })}`,
      { anonymous: true, signal },
    ),
  // Sent with the session (when there is one) so the API can apply the account's plan.
  catalogItem: (id: string, cityId?: string, storeIds?: string[]) =>
    apiRequest<CatalogItemDetail>(
      `/catalog/items/${id}?${queryString({ cityId, storeIds: storeIds?.join(",") })}`,
    ),
  // The public price page shows the same view to everyone and never spends an account's daily comparisons.
  publicCatalogItem: (id: string, cityId: string) =>
    apiRequest<CatalogItemDetail>(`/catalog/items/${id}?${queryString({ cityId })}`, { anonymous: true }),
  catalogItemHistory: (id: string, cityId: string, storeIds?: string[]) =>
    apiRequest<CatalogItemHistory>(
      `/catalog/items/${id}/history?${queryString({ cityId, storeIds: storeIds?.join(",") })}`,
    ),
  discoverProducts: (filters: ProductFilters & { cityId?: string; storeIds?: string[] } = {}, signal?: AbortSignal) =>
    apiRequest<PageResponse<ProductDiscoveryResult>>(
      `/products/discovery?${queryString({ ...filters, storeIds: filters.storeIds?.join(",") })}`,
      { anonymous: true, signal },
    ),
  states: (page = 0, size = 100) =>
    apiRequest<PageResponse<State>>(`/states?${queryString({ page, size })}`, {
      anonymous: true,
    }),
  cities: (stateId?: string, page = 0, size = 100) =>
    apiRequest<PageResponse<City>>(
      `/cities?${queryString({ stateId, page, size })}`,
      {
        anonymous: true,
      },
    ),
  products: (filters: ProductFilters = {}, signal?: AbortSignal) =>
    apiRequest<PageResponse<Product>>(
      `/products?${queryString({ size: 20, ...filters })}`,
      {
        anonymous: true,
        signal,
      },
    ),
  productFilters: (query: string, signal?: AbortSignal) =>
    apiRequest<ProductSearchFacets>(
      `/products/filters?${queryString({ query })}`,
      { anonymous: true, signal },
    ),
  product: (id: string) =>
    apiRequest<Product>(`/products/${id}`, { anonymous: true }),
  offers: (
    ids: string[],
    cityId: string,
    signal?: AbortSignal,
    storeIds: string[] = [],
  ) =>
    apiRequest<ProductOffers[]>(
      `/products/offers?${queryString({ ids: ids.join(","), cityId, storeIds: storeIds.join(",") })}`,
      { anonymous: true, signal },
    ),
  stores: (cityId?: string, page = 0, size = 100) =>
    apiRequest<PageResponse<Store>>(
      `/stores?${queryString({ cityId, page, size })}`,
      {
        anonymous: true,
      },
    ),
  allStores: async (cityId: string) => {
    const first = await catalogApi.stores(cityId);
    const stores = [...first.content];
    for (let page = 1; page < first.totalPages; page++) {
      stores.push(...(await catalogApi.stores(cityId, page)).content);
    }
    return stores;
  },
  compareAllProductOffers: async (
    productId: string,
    cityId: string,
    storeIds: string[] = [],
  ) => {
    const first = await catalogApi.compareProduct(
      productId,
      cityId,
      0,
      100,
      storeIds,
    );
    const content = [...first.stores.content];
    for (let page = 1; page < first.stores.totalPages; page++) {
      content.push(
        ...(
          await catalogApi.compareProduct(
            productId,
            cityId,
            page,
            100,
            storeIds,
          )
        ).stores.content,
      );
    }
    return { ...first, stores: { ...first.stores, content } };
  },
  store: (id: string) =>
    apiRequest<Store>(`/stores/${id}`, { anonymous: true }),
  storeProducts: (id: string, query: string, page = 0) =>
    apiRequest<PageResponse<StoreProduct>>(
      `/stores/${id}/products?${queryString({ query, page, size: 30 })}`,
      { anonymous: true },
    ),
  priceHistory: (productId: string, storeId: string, page = 0, size = 20) =>
    apiRequest<PageResponse<PriceRecord>>(
      `/prices?${queryString({ productId, storeId, page, size })}`,
    ),
  compareProduct: (
    productId: string,
    cityId: string,
    page = 0,
    size = 100,
    storeIds: string[] = [],
  ) =>
    apiRequest<ProductComparison>(
      `/comparisons/products?${queryString({ productId, cityId, page, size, storeIds: storeIds.join(",") })}`,
    ),
};

export const preferenceApi = {
  get: () => apiRequest<UserPreference>("/users/me/preferences"),
  setCity: (cityId: string) =>
    apiRequest<UserPreference>("/users/me/preferences/preferred-city", {
      method: "PUT",
      body: { cityId },
    }),
  clearCity: () =>
    apiRequest<UserPreference>("/users/me/preferences/preferred-city", {
      method: "DELETE",
    }),
  addFavoriteStore: (storeId: string) =>
    apiRequest<UserPreference>(
      `/users/me/preferences/favorite-stores/${storeId}`,
      { method: "PUT" },
    ),
  removeFavoriteStore: (storeId: string) =>
    apiRequest<UserPreference>(
      `/users/me/preferences/favorite-stores/${storeId}`,
      { method: "DELETE" },
    ),
};

export const shoppingListApi = {
  list: (page = 0, size = 20) =>
    apiRequest<PageResponse<ShoppingListSummary>>(
      `/shopping-lists?${queryString({ page, size })}`,
    ),
  get: (id: string) => apiRequest<ShoppingList>(`/shopping-lists/${id}`),
  create: (input: {
    name: string;
    shoppingType: "DAILY" | "WEEKLY" | "MONTHLY" | "CUSTOM";
  }) =>
    apiRequest<ShoppingList>("/shopping-lists", {
      method: "POST",
      body: input,
    }),
  update: (
    id: string,
    input: {
      name: string;
      shoppingType: "DAILY" | "WEEKLY" | "MONTHLY" | "CUSTOM";
    },
  ) =>
    apiRequest<ShoppingList>(`/shopping-lists/${id}`, {
      method: "PUT",
      body: input,
    }),
  remove: (id: string) =>
    apiRequest<void>(`/shopping-lists/${id}`, { method: "DELETE" }),
  addItem: (
    id: string,
    input: { productId?: string; catalogItemId?: string; quantity: number },
  ) =>
    apiRequest<ShoppingListItem>(`/shopping-lists/${id}/items`, {
      method: "POST",
      body: input,
    }),
  updateItem: (id: string, itemId: string, quantity: number) =>
    apiRequest<ShoppingListItem>(`/shopping-lists/${id}/items/${itemId}`, {
      method: "PUT",
      body: { quantity },
    }),
  removeItem: (id: string, itemId: string) =>
    apiRequest<void>(`/shopping-lists/${id}/items/${itemId}`, {
      method: "DELETE",
    }),
  compare: (
    id: string,
    cityId: string,
    page = 0,
    size = 100,
    storeIds: string[] = [],
  ) =>
    apiRequest<ShoppingListComparison>(
      `/comparisons/shopping-lists/${id}?${queryString({ cityId, page, size, storeIds: storeIds.join(",") })}`,
    ),
  /** Premium: cheaper equivalents per kg, litre or unit; the free plan gets only the count. */
  substitutes: (id: string, cityId: string) =>
    apiRequest<ListSubstitutes>(`/comparisons/shopping-lists/${id}/substitutes?${queryString({ cityId })}`),
  replaceItem: (id: string, itemId: string, input: { catalogItemId: string; quantity: number }) =>
    apiRequest<ShoppingListItem>(`/shopping-lists/${id}/items/${itemId}/replacement`, { method: "POST", body: input }),
  /** Premium: the items that are a good buy today; the free plan gets only the counts. */
  goodPrices: (id: string, cityId: string) =>
    apiRequest<ListGoodPrices>(`/comparisons/shopping-lists/${id}/good-prices?${queryString({ cityId })}`),
  recommendation: (id: string, cityId: string, storeIds: string[] = []) =>
    apiRequest<ShoppingRecommendation>(
      `/comparisons/shopping-lists/${id}/recommendation?${queryString({ cityId, storeIds: storeIds.join(",") })}`,
    ),
};

export const alertApi = {
  list: (page = 0, size = 20) =>
    apiRequest<PageResponse<PriceAlert>>(
      `/alerts?${queryString({ page, size })}`,
    ),
  create: (input: CreatePriceAlertInput) =>
    apiRequest<PriceAlert>("/alerts", { method: "POST", body: input }),
  deactivate: (id: string) =>
    apiRequest<PriceAlert>(`/alerts/${id}/deactivation`, { method: "PATCH" }),
  notifications: (page = 0, size = 20) =>
    apiRequest<PageResponse<AlertNotification>>(
      `/notifications?${queryString({ page, size })}`,
    ),
  unreadNotifications: () => apiRequest<{ unread: number }>("/notifications/unread-count"),
  markNotificationRead: (id: string) =>
    apiRequest<AlertNotification>(`/notifications/${id}/read`, {
      method: "PATCH",
    }),
};

export interface ContributionInput {
  productId: string;
  storeId: string;
  regularPrice: number;
  promotionalPrice?: number;
  currency: "BRL";
  observedAt: string;
  validUntil?: string;
  promotionValidUntil?: string;
  availability: StockAvailability;
}

export const contributionApi = {
  listMine: (page = 0, size = 20) =>
    apiRequest<PageResponse<PriceContribution>>(
      `/contributions?${queryString({ page, size })}`,
    ),
  submit: (input: ContributionInput) =>
    apiRequest<PriceContribution>("/contributions", {
      method: "POST",
      body: input,
    }),
  moderation: (
    status: "PENDING" | "APPROVED" | "REJECTED" = "PENDING",
    page = 0,
  ) =>
    apiRequest<PageResponse<PriceContribution>>(
      `/admin/contributions?${queryString({ status, page, size: 20 })}`,
    ),
  approve: (id: string) =>
    apiRequest<PriceContribution>(`/admin/contributions/${id}/approval`, {
      method: "POST",
    }),
  reject: (id: string, reason: string) =>
    apiRequest<PriceContribution>(`/admin/contributions/${id}/rejection`, {
      method: "POST",
      body: { reason },
    }),
};

export const adminApi = {
  audit: (page = 0, size = 20) =>
    apiRequest<PageResponse<AdminAudit>>(
      `/admin/audit?${queryString({ page, size })}`,
    ),
  registerSource: (input: {
    code: string;
    name: string;
    baseUrl: string;
    verifiedAt: string;
  }) => apiRequest("/admin/sources", { method: "POST", body: input }),
  ingestChain: (input: unknown) =>
    apiRequest("/admin/chains", { method: "POST", body: input }),
  ingestStore: (input: unknown) =>
    apiRequest("/admin/stores", { method: "POST", body: input }),
  ingestProduct: (input: unknown) =>
    apiRequest("/admin/products", { method: "POST", body: input }),
  recordPrice: (input: unknown) =>
    apiRequest("/admin/prices", { method: "POST", body: input }),
  collectionHealth: () => apiRequest<CollectionHealth>("/admin/collections/health"),
  collect: (collectorCode: string, existingOnly: boolean) =>
    apiRequest<unknown>(`/admin/collections?${queryString({ collectorCode, existingOnly: String(existingOnly) })}`, { method: "POST" }),
};

export const planApi = {
  /** Public: what a new free account gets (during the open beta, everything). */
  offer: () => apiRequest<{ openBeta: boolean }>("/plans/offer", { anonymous: true }),
};

export const subscriptionApi = {
  status: () => apiRequest<SubscriptionStatus>("/subscription"),
  startTrial: (billingCycle: BillingCycle) =>
    apiRequest<SubscriptionStatus>("/subscription/trial", { method: "POST", body: { billingCycle } }),
  cancelTrial: () => apiRequest<SubscriptionStatus>("/subscription/trial", { method: "DELETE" }),
  checkout: (billingCycle: BillingCycle) =>
    apiRequest<{ checkoutUrl: string }>("/subscription/checkout", { method: "POST", body: { billingCycle } }),
  cancelSubscription: () => apiRequest<SubscriptionStatus>("/subscription", { method: "DELETE" }),
  premiumValue: (cityId: string) =>
    apiRequest<PremiumValue>(`/subscription/premium-value?${queryString({ cityId })}`),
};

export interface DealFilters {
  cityId: string;
  storeId?: string;
  category?: string;
  /** Premium: only the products of the person's lists. Sent with the session; the rest is public. */
  onlyMyLists?: boolean;
  page?: number;
  size?: number;
}

export const dealApi = {
  list: ({ onlyMyLists = false, ...filters }: DealFilters) =>
    apiRequest<DealsPage>(
      `/deals?${queryString({ ...filters, onlyMyLists: onlyMyLists ? "true" : undefined })}`,
      { anonymous: !onlyMyLists },
    ),
};

export interface RecordPurchaseInput {
  /** Chosen here, so sending the same purchase again records it once. */
  id: string;
  storeId: string;
  items: Array<{ itemId: string; shelfPrice?: number }>;
}

export const tripApi = {
  trip: (listId: string, storeId: string) =>
    apiRequest<Trip>(`/shopping-lists/${listId}/trip?${queryString({ storeId })}`),
  check: (listId: string, itemId: string) =>
    apiRequest<ShoppingListItem>(`/shopping-lists/${listId}/items/${itemId}/check`, { method: "PUT" }),
  uncheck: (listId: string, itemId: string) =>
    apiRequest<ShoppingListItem>(`/shopping-lists/${listId}/items/${itemId}/check`, { method: "DELETE" }),
  clearChecks: (listId: string) => apiRequest<void>(`/shopping-lists/${listId}/checks`, { method: "DELETE" }),
  recordPurchase: (listId: string, input: RecordPurchaseInput) =>
    apiRequest<{ purchase: Purchase; listRestarted: boolean }>(`/shopping-lists/${listId}/purchases`, {
      method: "POST",
      body: input,
    }),
};

export const savingsApi = {
  /** No month: this month. */
  month: (month?: string) => apiRequest<SavingsReport>(`/savings?${queryString({ month })}`),
  deletePurchase: (id: string) => apiRequest<void>(`/purchases/${id}`, { method: "DELETE" }),
};

export const recurringListApi = {
  /** The person's weekly and monthly lists recalculated now in the city. */
  mine: (cityId: string) => apiRequest<RecurringList[]>(`/recurring-lists?${queryString({ cityId })}`),
};
